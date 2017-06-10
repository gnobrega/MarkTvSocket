package br.com.marktv.marktvsocket;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.ini4j.Ini;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * Created by Gustavo on 30/05/2017.
 */

public class Util {

    static ArrayList<LogPlayer> logs = new ArrayList<LogPlayer>();
    static Thread threadSendLog;

    /**
     * Carrega os dados de configurações
     */
    public static Ini.Section loadConfig(String section) {
        File configFile = new File(Global.pathFileIni);
        if( configFile.exists() ) {
            try {
                Ini configIni = null;
                configIni = new Ini(configFile);
                Ini.Section ftpConfig = configIni.get(section);
                return ftpConfig;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(Global.LOG_FILE, "Arquivo de configuração \"wikpix.ini\" não encontrado.");
        }
        return null;
    }

    /**
     * Log de console
     */
    public static void log(String msg) {
        Log.i(Global.LOG_FILE, msg);
    }

    /**
     * Recupera o nome do player
     */
    public static String getPlayerName() {
        Ini.Section configIni = Util.loadConfig("client");
        if( configIni != null ) {
            return configIni.get("key");
        } else {
            Log.e(Global.LOG_FILE, "Não foi possível carregar o arquivo INI");
        }
        return null;
    }

    /**
     * Recupera o id do player
     */
    public static Integer getPlayerId() {
        Ini.Section configIni = Util.loadConfig("player");
        if( configIni != null ) {
            return Integer.parseInt(configIni.get("id"));
        } else {
            Log.e(Global.LOG_FILE, "Não foi possível carregar o arquivo INI");
        }
        return null;
    }

    /**
     * Envia uma mensagem para o servidor
     */
    public static void sendLogServer() {

        final Integer playerId = getPlayerId();
        if( playerId != null ) {

            //Executa em background
            threadSendLog = new Thread(new Runnable() {
                public void run() {
                    String urlPing = Global.serverLogUrl + "/id/" +playerId;
                    while(true) {

                        //Reporta os logs
                        HashMap<String, String> params = new HashMap<String, String>();
                        if( logs.size() > 0 ) {
                            params.put("msg", logs.get(0).msg);
                            params.put("tipo", logs.get(0).tipo.toString());
                            String resp = Util.post(urlPing, params);
                            if( resp != null ) {
                                logs.remove(0);
                            }
                        }

                        SystemClock.sleep(10000);
                    }
                }
            });
            threadSendLog.setName("MarkTv Socket SendLogs");
            threadSendLog.start();
        }
    }

    /**
     * Adiciona uma mensagem para ser enviada ao servidor
     */
    public static void addLogServer(Integer tipo, String msg) {
        LogPlayer logPlayer = new LogPlayer(tipo, msg);
        logs.add(logPlayer);
    }

    /**
     * Envia uma requisição POST
     */
    public static String post(String sUrl, HashMap<String, String> params) {
        HttpURLConnection connection = null;
        URL url;
        try {
            String urlParameters = Util.paramsToString(params);

            //Create connection
            url = new URL(sUrl);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");

            connection.setRequestProperty("Content-Length", "" +
                    Integer.toString(urlParameters.getBytes().length));
            connection.setRequestProperty("Content-Language", "en-US");

            connection.setUseCaches (false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream (
                    connection.getOutputStream ());
            wr.writeBytes (urlParameters);
            wr.flush ();
            wr.close ();

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuffer response = new StringBuffer();
            while((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    /**
     * Converte os parâmetros em String
     */
    public static String paramsToString(HashMap<String, String> params) {
        String strParams = "";
        Boolean isFirst = true;
        for(Map.Entry<String, String> entry : params.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();
            if( isFirst != true ) {
                strParams += "&";
            }
            try {
                strParams += field + "=" + URLEncoder.encode(value, "ISO-8859-1");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            isFirst = false;
        }
        return strParams;
    }

    /**
     * Verifica se um aplicativo está em execução
     */
    public static Boolean appIsRunning(Context context, String pkg) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService( ACTIVITY_SERVICE );
        List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        Boolean isRunning = false;
        for(int i = 0; i < procInfos.size(); i++){
            if( procInfos.get(i).processName.equals(pkg) ) {
                isRunning = true;
                break;
            }
        }
        return isRunning;
    }
}
