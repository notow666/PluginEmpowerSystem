package net.noyark.www.utils;

import net.noyark.www.utils.ex.DBConnectException;
import net.noyark.www.utils.ex.ParseException;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * 这个类用于插件调用，连接远端的key服务器
 * 你必须设置userName和password
 *
 * 设置流程
 * 设置userName和password
 * connect
 * setTable
 * compareKey 返回值
 * 之后进行处理授权成功和失败的逻辑
 *
 * @author magiclu550
 */

public class DB_CONNECT implements Connector{

    static {
        connector = new DB_CONNECT();
    }

    private static DB_CONNECT connector;

    private String userName;

    private String password;

    private Connection connection;

    private Yaml yaml;

    private String table;

    private DBTypes type;

    private String ip;

    private String dbName;

    private DB_CONNECT(){
        yaml = new Yaml();
    }

    /**
     * 不定端口
     * @param ip
     * @param dbName
     * @param port
     * @param types
     */

    public void connect(String ip,String dbName,int port,DBTypes types){
        DBUtils utils = new DBUtils(types,userName,password,dbName,ip,port);
        this.type = types;
        this.ip = ip;
        this.dbName = dbName;
        try{
            connection = utils.getConnection();
        }catch (Exception e){
            throw new DBConnectException("connect error",e);
        }
    }

    /**
     * 默认连接mysql
     * @param ip
     * @param dbName
     */

    public void connect(String ip,String dbName){
        connect(ip,dbName,3306,DBTypes.MYSQL);
    }

    /**
     * 使用默认端口
     * @param ip
     * @param dbName
     * @param dbTypes
     */

    public void connect(String ip,String dbName,DBTypes dbTypes){
        connect(ip,dbName,dbTypes.getPort(),dbTypes);
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 设置当前的表
     * @param table
     */

    public void setTable(String table){
        this.table = table;
    }

    /**
     * 创建表
     */
    public boolean createKeyTable(String table){
        try{
            return connection.createStatement().execute("CREATE TABLE "+table+" (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "keyName CHAR(200) UNIQUE," +
                    "ip VARCHAR (50)," +
                    "port CHAR " +
                    ")");
        }catch (SQLException e){
            e.printStackTrace();
            return false;
        }
    }



    /**
     * 制定配置文件和配置字段，查找序列号是否符合要求,前提是设置了制定ip地址
     * 表的结构要求是
     * id        key            ip              port
     * primary  unique key
     * int      text           varchar(50)       varchar(10)
     * serverIp是指当前授权的ip地址
     * serverPort是指当前的授权port
     *
     * 返回是否已经授权
     */
    public boolean compareKey(String keyFile,String keyName,String serverIp,int serverPort){
        try{
            return compareKey(new FileInputStream(keyFile),keyName,serverIp,serverPort);
        }catch (IOException e){
            throw new ParseException("the yaml config is wrong",e);
        }
    }

    public boolean compareKey(InputStream in,String keyName,String serverIp,int serverPort){
        try{
            Map keyMapping = yaml.load(in);
            String key = keyMapping.get(keyName).toString();
            return compareKey(key,serverIp,serverPort);
        }catch (Exception e){
            throw new ParseException("the connection is wrong",e);
        }
    }

    /**
     * 前提连接了数据库
     * 生成随机序列码，插入数据库
     */
    public void randomKeys(int count){
        for(int i = 0;i<count;i++){
            randomKey();
        }
    }

    /**
     * 生成单个随机序列码
     */
    public void randomKey(){
        try{
            UUID uuid = UUID.randomUUID();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO "+table+" (id,keyName) VALUES (null,?)");
            statement.setString(1,uuid.toString());
            statement.executeUpdate();
        }catch (SQLException e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean compareKey(String key,String serverIp,int serverPort) throws SQLException{
        PreparedStatement statement = connection.prepareStatement("SELECT * FROM "+table+" WHERE keyName = ?");
        statement.setString(1,key);
        ResultSet set = statement.executeQuery();
        boolean found = false;
        while (set.next()){
            String ip = set.getString("ip");
            String port = set.getString("port");
            if((ip == null||"".equals(ip))&&(port==null||"".equals(port))){
                //如果不存在，则将ip和port插入进去
                PreparedStatement insertIp = connection.prepareStatement("INSERT INTO "+table+" (ip,port) VALUES (?,?) WHERE keyName=?");
                insertIp.setString(1,serverIp);
                insertIp.setString(2,serverPort+"");
                insertIp.setString(3,key);
                insertIp.executeUpdate();
                found = true;
            }else{
                if(serverIp.equals(ip)&&(serverPort+"").equals(port)) {
                    found = true;//ip port一样，那么该插件已经授权
                }
            }
        }
        return found;
    }

    public void close(){
        try{
            connection.close();
        }catch (SQLException e){
            e.printStackTrace();
        }
    }

    public static DB_CONNECT getConnector() {
        return connector;
    }

    public String getUserName(){
        return userName;
    }

    public String getPassword(){
        return password;
    }
    public int getPort(){
        return type.getPort();
    }
    public DBTypes getType(){
        return type;
    }
    public String getIp(){
        return ip;
    }

    public String getDbName(){
        return dbName;
    }

    public String getTable() {
        return table;
    }
}
