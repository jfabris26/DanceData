package database;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.sql.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;

/**
 * This is the database class. It is a sqlite database that connects to the file
 * in the database folder called scddata.db. It contains many functions for getting
 * the required data from the database to be put on screen. It also contains a
 * function to update the database upon the user request.
 */
public class Database {
	
	private Connection connection;
	private Statement stmt;
	private String query;
	private File dbFile;
	private File saveFile;
	private URL dbURL;
	
	public Database() throws SQLException, MalformedURLException {
		query = "";
		dbURL = new URL("http://media.strathspey.org/scddata/scddata-2.0.db");
		dbFile = new File("database/scddata.db");
		saveFile = new File("database/ihave.txt");
		init();
	}
	
	/**
	 * Initialize the database connection
	 * @throws SQLException
	 * @throws MalformedURLException
	 */
	private void init() throws SQLException, MalformedURLException {
		connection = connect();
		stmt = connection.createStatement();
		stmt.setQueryTimeout(30);
	}
	
	/** 
	 * Connect to the local sqlite database and return the connection
	 * @return
	 * @throws SQLException
	 */
	private Connection connect() throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:database/scddata.db");
	}
	
	/**
	 * Close down the databases
	 * @throws SQLException
	 */
	public void close() throws SQLException {
		if(stmt != null)
			stmt.close();
			stmt = null;
		if(connection != null)
			connection.close();
			connection = null;
	}
	
	/**
	 * Remember what stuff the user has in their collection
	 * Downloads the most recent sqlite db file from the online source
	 * Add the ihave and tag columns back to the db
	 * Load the stuff we saved earlier back into the db
	 * @return 1 on success; 0 when no internet connection; -1 on error, 
	 * 		-2 on fatal error (app needs to restart)
	 */
	public int update() {
		try {
			try {
				saveIHave();
			} catch(Exception e) {
				System.out.println("Save failed");
			}
			close();
			FileUtils.copyURLToFile(dbURL, dbFile);
			init();
			addIHaveTagColumns();
			loadIHave();
			return 1;
		} catch(UnknownHostException | SocketException e) {
			try {
				close();
				init();
			} catch (Exception e1) {
				return -2;
			}
			return 0;
		} catch(Exception e) {
			try {
				close();
				init();
			} catch (Exception e1) {
				return -2;
			}
			return -1;
		}
	}
	
	/**
	 * Add the necessary columns to be able to tag dances, albums, and publications as 
	 * having in a personal collection.
	 * @throws SQLException
	 */
	public void addIHaveTagColumns() throws SQLException {
		stmt.execute("ALTER TABLE dance ADD ihave TINYINT(1) DEFAULT 0");
		stmt.execute("ALTER TABLE dance ADD COLUMN tag VARCHAR(256) DEFAULT NULL");
		stmt.execute("ALTER TABLE album ADD COLUMN ihave TINYINT(1) DEFAULT 0");
		stmt.execute("ALTER TABLE album ADD COLUMN tag VARCHAR(256) DEFAULT NULL");
		stmt.execute("ALTER TABLE publication ADD COLUMN ihave TINYINT(1) DEFAULT 0");
		stmt.execute("ALTER TABLE publication ADD COLUMN tag VARCHAR(256) DEFAULT NULL");
		stmt.execute("ALTER TABLE recording ADD COLUMN ihave TINYINT(1) DEFAULT 0");
		stmt.execute("ALTER TABLE recording ADD COLUMN tag VARCHAR(256) DEFAULT NULL");
	}
	
	/**
	 * Save the information for what the user has in their personal collection. 
	 * Writes the type (table), the id, and the tag to a file. We will use this information
	 * when we update to preserve this information.
	 * 
	 * @throws SQLException
	 * @throws IOException
	 */
	public void saveIHave() throws SQLException, IOException {
		
		// dance
		query = "SELECT id, tag FROM dance WHERE ihave=1";
		ResultSet rs = stmt.executeQuery(query);
		String info;
		boolean append = false;
		while(rs.next()) {
			info = "dance " + rs.getInt("id") + " " + rs.getString("tag");
			FileUtils.writeStringToFile(saveFile, info, append);
			append = true;
			FileUtils.writeStringToFile(saveFile, "\n", append);
		}
		
		// album
		query = "SELECT id, tag FROM album WHERE ihave=1";
		rs = stmt.executeQuery(query);
		while(rs.next()) {
			info = "album " + rs.getInt("id") + " " + rs.getString("tag");
			FileUtils.writeStringToFile(saveFile, info, append);
			append = true;
			FileUtils.writeStringToFile(saveFile, "\n", append);
		}
		
		// publication
		query = "SELECT id, tag FROM publication WHERE ihave=1";
		rs = stmt.executeQuery(query);
		while(rs.next()) {
			info = "publication " + rs.getInt("id") + " " + rs.getString("tag");
			FileUtils.writeStringToFile(saveFile, info, append);
			append = true;
			FileUtils.writeStringToFile(saveFile, "\n", append);
		}
		
		// recording
		query = "SELECT id, tag FROM recording WHERE ihave=1";
		rs = stmt.executeQuery(query);
		while(rs.next()) {
			info = "recording " + rs.getInt("id") + " " + rs.getString("tag");
			FileUtils.writeStringToFile(saveFile, info, append);
			append = true;
			FileUtils.writeStringToFile(saveFile, "\n", append);
		}
	}
	
	/**
	 * After updating the database, load the collection information back into the database
	 * @throws IOException
	 * @throws SQLException
	 */
	public void loadIHave() throws IOException, SQLException {
		Iterator<String> iter = FileUtils.readLines(saveFile).iterator();
		String line;
		String[] info;
		while(iter.hasNext()) {
			line = iter.next();
			info = line.split(" ");
			if(info.length == 2 || info[2].equals("null"))
				query = "UPDATE " + info[0] + " SET ihave=1 WHERE id=" + info[1];
			else
				query = "UPDATE " + info[0] + " SET ihave=1, tag='" + info[2] + "' WHERE id=" + info[1];
			stmt.execute(query);
		}
	}
	
	/**
	 * Mark as having in personal collection. 
	 * @param table - the type of thing i have (can be album, recording, publication, or dance)
	 * @param id - the id 
	 * @throws SQLException
	 */
	public void iHave(String table, int id) throws SQLException {
		query = "UPDATE " + table + " SET ihave=1 WHERE id=" + id;
		stmt.execute(query);
		if(table.equals("publication")) {
			query = "UPDATE dance SET ihave=1 WHERE id in "
					+ "(SELECT dance_id FROM dancespublicationsmap WHERE publication_id=" + id + ")";
			stmt.execute(query);
		} else if(table.equals("album")) {
			query = "UPDATE recording SET ihave=1 WHERE id in "
					+ "(SELECT recording_id FROM albumsrecordingsmap WHERE album_id=" + id + ")";
			stmt.execute(query);
		}
	}

	/**
	 * Mark as not having in personal collection
	 * @param table - the type (album, recording, publication, or dance)
	 * @param id - the id
	 * @throws SQLException
	 */
	public void iDontHave(String table, int id) throws SQLException {
		query = "UPDATE " + table + " SET ihave=0 WHERE id=" +id;
		stmt.execute(query);
		if(table.equals("publication")) {
			query = "UPDATE dance SET ihave=0 WHERE id in "
					+ "(SELECT dance_id FROM dancespublicationsmap WHERE publication_id=" + id + ")";
			stmt.execute(query);
		} else if(table.equals("album")) {
			query = "UPDATE recording SET ihave=0 WHERE id in "
					+ "(SELECT recording_id FROM albumsrecordingsmap WHERE album_id=" + id + ")";
			stmt.execute(query);
		}
	}

	/**
	 * Give the item a tag
	 * @param table - the type (album, recording, publication, or dance)
	 * @param id - the id
	 * @param tag - the tag string
	 * @throws SQLException
	 */
	public void addTag(String table, int id, String tag) throws SQLException {
		query = "UPDATE " + table + " SET tag='" + tag + "' WHERE id=" + id;
		stmt.execute(query);
		if(table.equals("publication")) {
			query = "UPDATE dance SET tag='" + tag + "' WHERE id in "
					+ "(SELECT dance_id FROM dancespublicationsmap WHERE publication_id=" + id + ")";
			stmt.execute(query);
		} else if(table.equals("album")) {
			query = "UPDATE recording SET tag='" + tag + "' WHERE id in "
					+ "(SELECT recording_id FROM albumsrecordingsmap WHERE album_id=" + id + ")";
			stmt.execute(query);
		}
	}

	/**
	 * Remove the items tag
	 * @param table - the type (album, recording, publicaiton, or dance)
	 * @param id - the id
	 * @throws SQLException 
	 */
	public void removeTag(String table, int id) throws SQLException {
		query = "UPDATE " + table + " SET tag=null WHERE id=" +id;
		stmt.execute(query);
		if(table.equals("publication")) {
			query = "UPDATE dance SET tag=null WHERE id in "
					+ "(SELECT dance_id FROM dancespublicationsmap WHERE publication_id=" + id + ")";
			stmt.execute(query);
		} else if(table.equals("album")) {
			query = "UPDATE recording SET tag=null WHERE id in "
					+ "(SELECT recording_id FROM albumsrecordingsmap WHERE album_id=" + id + ")";
			stmt.execute(query);
		}
	}

	/**
	 * Gets all information about a person in the database with the given id
	 * 
	 * @param id - the person's id in the database
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getPerson(int id) throws SQLException {
		query = "SELECT * FROM person WHERE id="+ id;
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get person's name from their id
	 * @param id
	 * @return String
	 * @throws SQLException
	 */
	public String getPersonName(int id) throws SQLException {
		query = "SELECT name FROM person WHERE id=" + id;
		return stmt.executeQuery(query).getString("name");
	}
	
	/**
	 * Search the table and return all records where name contains the param name
	 * @param table - the table to search in
	 * @param name - the name to search for
	 * @param ihave - if true only show what is marked as ihave, otherwise show all results
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet searchTableByName(String table, String name, boolean ihave) throws SQLException {
		name = name.replace("'", "''");
		if(table.equals("dance")) {
			query = "SELECT d.*, dt.name as type, mt.description as medleytype, s.name as shape, "
					+ "c.name as couples, p.name as progression, pb.name as publication, pn.name as devisor FROM dance d "
					+ "LEFT OUTER JOIN dancetype dt ON d.type_id=dt.id "
					+ "LEFT OUTER JOIN medleytype mt ON d.medleytype_id=mt.id "
					+ "LEFT OUTER JOIN shape s ON d.shape_id=s.id "
					+ "LEFT OUTER JOIN couples c ON d.couples_id=c.id "
					+ "LEFT OUTER JOIN progression p ON d.progression_id=p.id "
					+ "LEFT OUTER JOIN dancespublicationsmap dpm ON d.id=dpm.dance_id "
					+ "LEFT OUTER JOIN publication pb ON dpm.publication_id=pb.id "
					+ "LEFT OUTER JOIN person pn ON d.devisor_id=pn.id "
					+ "WHERE d.name like '%" + name + "%'";
			if(ihave) {
				query += " AND d.ihave=1";
			}
			query += " ORDER by name";
		} else if(table.equals("album")) {
			query = "SELECT a.*, p.name as artist FROM album a "
					+ "LEFT OUTER JOIN person p ON a.artist_id=p.id "
					+ "WHERE a.name like '%" + name + "%'";
			if(ihave) {
				query += " AND a.ihave=1";
			}
			query += " ORDER by name";
		} else if(table.equals("publication")) {
			query = "SELECT pb.*, pr.name as devisor FROM publication pb "
					+ "LEFT OUTER JOIN person pr ON pb.devisor_id=pr.id WHERE pb.name like '%" + name + "%'";
			if(ihave) {
				query += " AND pb.ihave=1";
			}
			query += " ORDER by name";
		} else if(table.equals("recording")){
			query = "SELECT r.*, dt.name as type, mt.description as medleytype, p.name as phrasing, pn.name as artist "
					+ "FROM recording r LEFT OUTER JOIN dancetype dt ON r.type_id=dt.id "
					+ "LEFT OUTER JOIN medleytype mt ON r.medleytype_id=mt.id "
					+ "LEFT OUTER JOIN phrasing p ON r.phrasing_id=p.id "
					+ "LEFT OUTER JOIN person pn ON r.artist_id=pn.id "
					+ "WHERE r.name like '%" + name + "%'";
			if(ihave) {
				query += " AND r.ihave=1";
			}
			query += " ORDER by name";
		} else {
			query = "SELECT * FROM " + table + " WHERE name like '%" + name + "%'";
			if(ihave) {
				query += " AND ihave=1";
			}
		}
		return stmt.executeQuery(query);
	}
	
	/**
	 * Search the table with specified advanced search params
	 * @param table - the table to search in
	 * @param name - the name to search for
	 * @param map - the mapping of keys and values in the advanced search
	 * @param ihave - if true only show what is marked as ihave, otherwise show all results
	 * @return ResultSet
	 * @throws SQLException
	 */
	
	public ResultSet advancedTableSearch(String table, String name, Map<String,String> map, boolean ihave) throws SQLException{
		if(table.equals("dance")) {
			query = "SELECT d.*, dt.name as type, mt.description as medleytype, s.name as shape, "
					+ "c.name as couples, p.name as progression, pb.name as publication, pn.name as devisor "
					+ "FROM dance d "
					+ "LEFT OUTER JOIN dancetype dt ON d.type_id=dt.id "
					+ "LEFT OUTER JOIN medleytype mt ON d.medleytype_id=mt.id "
					+ "LEFT OUTER JOIN shape s ON d.shape_id=s.id "
					+ "LEFT OUTER JOIN couples c ON d.couples_id=c.id "
					+ "LEFT OUTER JOIN progression p ON d.progression_id=p.id "
					+ "LEFT OUTER JOIN dancespublicationsmap dpm ON d.id=dpm.dance_id "
					+ "LEFT OUTER JOIN publication pb ON dpm.publication_id=pb.id "
					+ "LEFT OUTER JOIN person pn ON d.devisor_id=pn.id ";
			if (name.length() != 0){
				if (name.contains("'"))
					query += "WHERE d.name like '%" + name.replace("'", "''") + "%'";
				else
					query += "WHERE d.name like '%" + name + "%'";
			}
			else
				query += "WHERE d.name like '%%'";
			Object[] keys = map.keySet().toArray();
			for (int i=0; keys.length>i; i++){
				String param = (String)keys[i];
				String val = map.get(keys[i]);
				if(!val.isEmpty()){
					val.replace("'", "''");
					if (param.equals("bars")){
						query += " AND d.barsperrepeat"+val;
					}
					else if (param.equals("author")){
						query += " AND pn.name like '%"+val+"%'";
					}
					else if (param.equals("type")){
						query += " AND dt.name='"+ val +"'";
					}
					else if (param.equals("couples")){
						query += " AND c.name='"+ val +"'";
					}
					else if (param.equals("shape")){
						query += " AND s.name='"+ val +"'";
					}
					else if (param.equals("formation") && !(val.contains("*  *  *  *  *"))){
						String[] formations = new String[5];
						int len = val.length();
						for (int k=0,j=0,count=0; k<len; k++){
							if (val.substring(k,k+1).equals("~")){
								if (val.substring(j,k).equals("and") || val.substring(j,k).equals("or")){
									formations[count] = val.substring(j,k);
								}
								else if (val.substring(j,k).equals("not")){
									formations[count] = "and " +val.substring(j,k);
								}
								else{
									formations[count] = stmt.executeQuery("SELECT id FROM formation WHERE name='"+val.substring(j,k)+"'").getString(1);
								}
								count++;
								j=k+1;
							}
							else if(val.substring(k,k+1).equals("*")){
								j=k+2;
								count++;
							}
							else if(val.substring(k,k+1).equals("'")){
								val = val.substring(0,k) + "'" + val.substring(k+1);
							}
						}
						if (formations[0] != null){
							query += " AND (d.id IN (SELECT dfm.dance_id FROM dancesformationsmap dfm";
							
							query+= " WHERE dfm.formation_id='" + formations[0] +"')";
							if (formations[1] != null && formations[2] != null){
								query+= " " +formations[1] + " d.id IN (SELECT dfm.dance_id FROM dancesformationsmap dfm"
										+ " WHERE dfm.formation_id='"+formations[2] +"')";
								if (formations[3] != null && formations[4] != null)
									query+= " "+ formations[3] + " d.id IN (SELECT dfm.dance_id FROM dancesformationsmap dfm"
											+ " WHERE dfm.formation_id='" + formations[4] +"'))";
								else
									query += ")";
							}
							else
								query += ")";
						}
						
					}
					else if (param.equals("steps")  && !(val.contains("*  *  *  *  *"))){
						String[] steps = new String[5];
						int len = val.length();
						for (int k=0,j=0,count=0; k<len; k++){
							if (val.substring(k,k+1).equals("~")){
								if (val.substring(j,k).equals("and") || val.substring(j,k).equals("or")){
									steps[count] = val.substring(j,k);
								}
								else if (val.substring(j,k).equals("not")){
									steps[count] = "and " +val.substring(j,k);
								}
								else{
									steps[count] = stmt.executeQuery("SELECT id FROM step WHERE name='"+val.substring(j,k)+"'").getString(1);
								}
								count++;
								j=k+1;
							}
							else if(val.substring(k,k+1).equals("*")){
								j=k+2;
								count++;
							}
							else if(val.substring(k,k+1).equals("'")){
								val = val.substring(0,k) + "'" + val.substring(k+1);
							}
						}
						if (steps[0] != null){
							query += " AND (d.id IN (SELECT dsm.dance_id FROM dancesstepsmap dsm";
							
							query+= " WHERE dsm.step_id='" + steps[0] +"')";
							if (steps[1] != null && steps[2] != null){
								query+= " " +steps[1] + " d.id IN (SELECT dsm.dance_id FROM dancesstepsmap dsm"
										+ " WHERE dsm.step_id='"+steps[2] +"')";
								if (steps[3] != null && steps[4] != null)
									query+= " "+ steps[3] + " d.id IN (SELECT dsm.dance_id FROM dancesstepsmap dsm"
											+ " WHERE dsm.step_id='" + steps[4] +"'))";
								else
									query += ")";
							}
							else
								query += ")";
						}
					}
					else if (param.equals("RSCDS")){
						if (val.equals("1")){
							query += " AND d.id IN (SELECT dpm.dance_id FROM dancespublicationsmap dpm "
									+ "LEFT OUTER JOIN publication pb "
									+ "WHERE dpm.publication_id=pb.id AND pb.shortname like '%RSCDS%')";
						}
					}
				}
			}
			if(ihave) {
				query += " AND d.ihave=1";
			}
			query += " GROUP by d.name, publication";
		}
		else if(table.equals("publication")) {
			query = "SELECT pb.*, pr.name as devisor FROM publication pb "
					+ "LEFT OUTER JOIN person pr ON pb.devisor_id=pr.id ";
			if (name.length() != 0){
				if (name.contains("'"))
					query += "WHERE pb.name like '%" + name.replace("'", "''") + "%'";
				else
					query += "WHERE pb.name like '%" + name + "%'";
			}
			else
				query += "WHERE pb.name like '%%'";
			String author = map.get("author");
			author = author.replace("'", "''");
			String rscds = map.get("RSCDS");
			if (!author.isEmpty())
				query += " AND pr.name like '%"+author+"%'";
			if (rscds.equals("1"))
				query += " AND pb.rscds";
			if(ihave) {
				query += " AND pb.ihave=1";
			}
			query += " ORDER by name";
		}
		else if(table.equals("recording")){
			query = "SELECT r.*, dt.name as type, mt.description as medleytype, p.name as phrasing, pn.name as artist "
					+ "FROM recording r LEFT OUTER JOIN dancetype dt ON r.type_id=dt.id "
					+ "LEFT OUTER JOIN medleytype mt ON r.medleytype_id=mt.id "
					+ "LEFT OUTER JOIN phrasing p ON r.phrasing_id=p.id "
					+ "LEFT OUTER JOIN person pn ON r.artist_id=pn.id ";
			if (name.length() != 0){
				if (name.contains("'"))
					query += "WHERE r.name like '%" + name.replace("'", "''") + "%'";
				else
					query += "WHERE r.name like '%" + name + "%'";
			}
			else
				query += "WHERE r.name like '%%'";
			String type = map.get("type");
			type= type.replace("'", "''");
			String medley = map.get("medley type");
			medley = medley.replace("'", "''");
			String repetitions = map.get("repetitions");
			repetitions= repetitions.replace("'", "''");
			String bars = map.get("bars");
			bars = bars.replace("'", "''");
			if (type != null)
				if (!type.isEmpty())
					query += " AND dt.name='"+type+"'";
			if (medley != null)
				if (!medley.isEmpty())
					query += " AND mt.description='"+medley+"'";
			if (repetitions != null)
				if (!repetitions.isEmpty())
					query += " AND r.repetitions"+repetitions;
			if (bars != null)
				if (!bars.isEmpty())
					query += " AND r.barsperrepeat"+bars;
			if(ihave) {
				query += " AND r.ihave=1";
			}
			query += " ORDER by name";
		} 
		else if(table.equals("album")) {
			query = "SELECT a.*, p.name as artist FROM album a "
					+ "LEFT OUTER JOIN person p ON a.artist_id=p.id ";
			if (name.length() != 0){
				if (name.contains("'"))
					query += "WHERE a.name like '%" + name.replace("'", "''") + "%'";
				else
					query += "WHERE a.name like '%" + name + "%'";
			}
			else
				query += "WHERE a.name like '%%'";
			String artist = map.get("artist_id");
			artist = artist.replace("'", "''");
			String year = map.get("productionyear");
			year = year.replace("'", "''");
			if (artist != null)
				if(!artist.isEmpty())
					query += " AND p.name like '%"+artist+"%'";
			if (year != null)
				if (!year.isEmpty())
					query += " AND a.productionyear="+year;
			if(ihave) {
				query += " AND a.ihave=1";
			}
			query += " ORDER by name";
		}
		else {
			query = "SELECT * FROM " + table + " WHERE name like '%" + name + "%'";
			if(ihave) {
				query += " AND ihave=1";
			}
		}
		System.out.println(query);
		return stmt.executeQuery(query);
	}
	
	/**
	 * Search the given table and return the name of the record with the given id
	 * @param table - the table to search in
	 * @param id - the id to find
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getNameByIdFromTable(String table, int id) throws SQLException {
		query = "SELECT name FROM '" + table +"' WHERE id=" + id;
		return stmt.executeQuery(query);
	}
	
	/**
	 * Search the given table and return all data for the given id
	 * @param table - the table to search in
	 * @param id - the id to find
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getAllByIdFromTable(String table, int id) throws SQLException {
		query = "SELECT * FROM '" + table +"' WHERE id=" + id;
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get a list of songs on the album with album_id
	 * @param album_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getRecordingsByAlbum(int album_id) throws SQLException {
		query = "SELECT r.*, dt.name as type, mt.description as medleytype, p.name as phrasing, pn.name as artist, tracknumber "
				+ "FROM recording r LEFT OUTER JOIN dancetype dt ON r.type_id=dt.id "
				+ "LEFT OUTER JOIN medleytype mt ON r.medleytype_id=mt.id "
				+ "LEFT OUTER JOIN phrasing p ON r.phrasing_id=p.id "
				+ "LEFT OUTER JOIN albumsrecordingsmap arm ON r.id=arm.recording_id "
				+ "LEFT OUTER JOIN person pn ON r.artist_id=pn.id "
				+ "WHERE arm.album_id=" + album_id + " ORDER BY tracknumber";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get all recordings for a given dance
	 * @param dance_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getRecordingsByDance(int dance_id) throws SQLException {
		query = "SELECT r.*, dt.name as type, mt.description as medleytype, p.name as phrasing, pn.name as artist "
				+ "FROM recording r LEFT OUTER JOIN dancetype dt ON r.type_id=dt.id "
				+ "LEFT OUTER JOIN medleytype mt ON r.medleytype_id=mt.id "
				+ "LEFT OUTER JOIN phrasing p ON r.phrasing_id=p.id "
				+ "LEFT OUTER JOIN person pn ON r.artist_id=pn.id "
				+ "LEFT OUTER JOIN dancesrecordingsmap drm ON r.id=drm.recording_id "
				+ "WHERE drm.dance_id=" + dance_id + " ORDER BY r.name";
		return stmt.executeQuery(query);
	}

	/** 
	 * Get recordings for the given tune
	 * @param tune_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getRecordingsByTune(int tune_id) throws SQLException {
		query = "SELECT r.*, dt.name as type, mt.description as medleytype, p.name as phrasing, pn.name as artist "
				+ "FROM recording r LEFT OUTER JOIN dancetype dt ON r.type_id=dt.id "
				+ "LEFT OUTER JOIN medleytype mt ON r.medleytype_id=mt.id "
				+ "LEFT OUTER JOIN phrasing p ON r.phrasing_id=p.id "
				+ "LEFT OUTER JOIN person pn ON r.artist_id=pn.id "
				+ "LEFT OUTER JOIN tunesrecordingsmap trm ON r.id=trm.recording_id "
				+ "WHERE trm.tune_id=" + tune_id + " ORDER BY r.name";
		return stmt.executeQuery(query);
	}

	/**
	 * Get all recordings by the artist with person_id
	 * @param person_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getRecordingsByPerson(int person_id) throws SQLException {
		query = "SELECT r.*, dt.name as type, mt.description as medleytype, p.name as phrasing, pn.name as artist "
				+ "FROM recording r LEFT OUTER JOIN dancetype dt ON r.type_id=dt.id "
				+ "LEFT OUTER JOIN medleytype mt ON r.medleytype_id=mt.id "
				+ "LEFT OUTER JOIN phrasing p ON r.phrasing_id=p.id "
				+ "LEFT OUTER JOIN person pn ON r.artist_id=pn.id "
				+ "WHERE pn.id=" + person_id + " ORDER BY r.name";
		return stmt.executeQuery(query);
	}

	/**
	 * Get all the steps for a given dance
	 * @param dance_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getStepsByDance(int dance_id) throws SQLException {
		query = "SELECT s.* FROM step s LEFT OUTER JOIN dancesstepsmap dsm "
				+ "ON s.id=dsm.step_id WHERE dsm.dance_id=" + dance_id + " ORDER BY s.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get all the tunes for a given dance
	 * @param dance_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getTunesByDance(int dance_id) throws SQLException {
		query = "SELECT t.*, p.name as composer FROM tune t LEFT OUTER JOIN dancestunesmap dtm ON t.id=dtm.tune_id "
				+ "LEFT OUTER JOIN person p ON t.composer_id=p.id WHERE dtm.dance_id=" + dance_id + " ORDER BY t.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get a list of tues in the publication with publication_id
	 * @param publication_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getTunesByPublication(int publication_id) throws SQLException {
		query = "SELECT t.*, p.name as composer FROM tune t LEFT OUTER JOIN tunespublicationsmap tpm "
				+ "ON t.id=tpm.tune_id LEFT OUTER JOIN person p ON t.composer_id=p.id "
				+ "WHERE tpm.publication_id=" + publication_id + " ORDER BY t.name";
		return stmt.executeQuery(query);
	}

	/**
	 * Get all tunes composed by the person with person_id
	 * @param person_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getTunesByPerson(int person_id) throws SQLException {
		query = "SELECT t.*, p.name as composer FROM tune t "
				+ "LEFT OUTER JOIN person p ON t.composer_id=p.id "
				+ "WHERE p.id=" + person_id + " ORDER BY t.name";
		return stmt.executeQuery(query);
	}

	/**
	 * Get all tunes for a given recording
	 * @param recording_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getTunesByRecording(int recording_id) throws SQLException {
		query = "SELECT t.*, p.name as composer FROM tune t LEFT OUTER JOIN tunesrecordingsmap trm ON t.id=trm.tune_id "
				+ "LEFT OUTER JOIN person p ON t.composer_id=p.id WHERE trm.recording_id=" + recording_id + " ORDER BY t.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get album for a given recording
	 * @param recording_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getAlbumByRecording(int recording_id) throws SQLException {
		query = "SELECT a.*, p.name as artist FROM album a LEFT OUTER JOIN albumsrecordingsmap arm ON a.id=arm.album_id "
				+ "LEFT OUTER JOIN person p ON a.artist_id=p.id WHERE arm.recording_id=" + recording_id + " ORDER BY a.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get all albums by the artist with person_id
	 * @param person_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getAlbumsByPerson(int person_id) throws SQLException {
		query = "SELECT a.*, p.name as artist FROM album a "
				+ "LEFT OUTER JOIN person p ON a.artist_id=p.id "
				+ "WHERE p.id=" + person_id + " ORDER BY a.name";
		return stmt.executeQuery(query);
	}

	/**
	 * Get a list of dances in the publication with publication_id
	 * @param publication_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getDancesByPublication(int publication_id) throws SQLException {
		query = "SELECT d.*, dt.name as type, mt.description as medleytype, s.name as shape, "
				+ "c.name as couples, p.name as progression, pb.name as publication, pn.name as devisor FROM dance d "
				+ "LEFT OUTER JOIN dancetype dt ON d.type_id=dt.id "
				+ "LEFT OUTER JOIN medleytype mt ON d.medleytype_id=mt.id "
				+ "LEFT OUTER JOIN shape s ON d.shape_id=s.id "
				+ "LEFT OUTER JOIN couples c ON d.couples_id=c.id "
				+ "LEFT OUTER JOIN progression p ON d.progression_id=p.id "
				+ "LEFT OUTER JOIN dancespublicationsmap dpm ON d.id=dpm.dance_id "
				+ "LEFT OUTER JOIN publication pb ON dpm.publication_id=pb.id "
				+ "LEFT OUTER JOIN person pn ON d.devisor_id=pn.id "
				+ "WHERE dpm.publication_id=" + publication_id + " ORDER BY d.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get the dances for a given tune
	 * @param tune_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getDancesByTune(int tune_id) throws SQLException {
		query = "SELECT d.*, dt.name as type, mt.description as medleytype, s.name as shape, "
				+ "c.name as couples, p.name as progression, pb.name as publication, pn.name as devisor FROM dance d "
				+ "LEFT OUTER JOIN dancetype dt ON d.type_id=dt.id "
				+ "LEFT OUTER JOIN medleytype mt ON d.medleytype_id=mt.id "
				+ "LEFT OUTER JOIN shape s ON d.shape_id=s.id "
				+ "LEFT OUTER JOIN couples c ON d.couples_id=c.id "
				+ "LEFT OUTER JOIN progression p ON d.progression_id=p.id "
				+ "LEFT OUTER JOIN dancespublicationsmap dpm ON d.id=dpm.dance_id "
				+ "LEFT OUTER JOIN publication pb ON dpm.publication_id=pb.id "
				+ "LEFT OUTER JOIN person pn ON d.devisor_id=pn.id "
				+ "LEFT OUTER JOIN dancestunesmap dtm ON d.id=dtm.dance_id "
				+ "WHERE dtm.tune_id=" + tune_id + " ORDER BY d.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get all dances devised by the person with person_id
	 * @param person_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getDancesByPerson(int person_id) throws SQLException {
		query = "SELECT d.*, dt.name as type, mt.description as medleytype, s.name as shape, "
				+ "c.name as couples, p.name as progression, pb.name as publication, pn.name as devisor FROM dance d "
				+ "LEFT OUTER JOIN dancetype dt ON d.type_id=dt.id "
				+ "LEFT OUTER JOIN medleytype mt ON d.medleytype_id=mt.id "
				+ "LEFT OUTER JOIN shape s ON d.shape_id=s.id "
				+ "LEFT OUTER JOIN couples c ON d.couples_id=c.id "
				+ "LEFT OUTER JOIN progression p ON d.progression_id=p.id "
				+ "LEFT OUTER JOIN dancespublicationsmap dpm ON d.id=dpm.dance_id "
				+ "LEFT OUTER JOIN publication pb ON dpm.publication_id=pb.id "
				+ "LEFT OUTER JOIN person pn ON d.devisor_id=pn.id "
				+ "WHERE pn.id=" + person_id + " ORDER BY d.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get all publications devised by the person with person_id
	 * @param person_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getPublicationsByPerson(int person_id) throws SQLException {
		query = "SELECT p.*, pn.name as devisor FROM publication p "
				+ "LEFT OUTER JOIN person pn ON p.devisor_id=pn.id "
				+ "WHERE pn.id=" + person_id + " ORDER BY p.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get all publications that the dance is found in
	 * @param dance_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getPublicationsByDance(int dance_id) throws SQLException {
		query = "SELECT p.*, pn.name as devisor FROM publication p "
				+ "LEFT OUTER JOIN person pn ON p.devisor_id=pn.id "
				+ "LEFT OUTER JOIN dancespublicationsmap dpm ON p.id=dpm.publication_id "
				+ "WHERE dpm.dance_id=" + dance_id + " ORDER BY p.name";
		return stmt.executeQuery(query);
	}
	
	/**
	 * Get all the formations for a given dance
	 * @param dance_id
	 * @return ResultSet
	 * @throws SQLException
	 */
	public ResultSet getFormationsByDance(int dance_id) throws SQLException {
		query = "SELECT f.* FROM formation f LEFT OUTER JOIN dancesformationsmap dfm "
				+ "ON f.id=dfm.formation_id WHERE dfm.dance_id=" + dance_id + " ORDER BY f.name";
		return stmt.executeQuery(query);
	}

	public ResultSet doQuery(String s) throws SQLException {
		query = s;
		return stmt.executeQuery(query);
	}
	
	public String getQuery() {
		return query;
	}
	
	/* Use for debugging to print the results of a query */
	public void printResults(ResultSet resultSet) throws SQLException {
		ResultSetMetaData rsmd = resultSet.getMetaData();
		int columnsNumber = rsmd.getColumnCount();
		int count = 1;
		while (resultSet.next()) {
			System.out.println("Record: " + count);
		    for (int i = 1; i <= columnsNumber; i++) {
		        String columnValue = resultSet.getString(i);
		        System.out.print(rsmd.getColumnName(i) + ": " + columnValue + ", ");
		    }
		    System.out.println("");
		    count++;
		}
	}
}