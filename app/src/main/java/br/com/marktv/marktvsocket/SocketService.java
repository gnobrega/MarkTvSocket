package br.com.marktv.marktvsocket;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static br.com.marktv.marktvsocket.Global.SELF_PACKAGE;

/**
 * Created by Gustavo on 25/05/2017.
 */

public class SocketService extends Service {
    private ServerSocket serverSocket;
    private String LOG_TAG = "SocketService";
    public static int PORT = 8123;
    private String DS_PACKAGE = "air.me.signage.player";
    private long DS_SLEEP = 0;
    private Thread serverThread;
    private Thread watchThread;
    private Integer totalRestart = 0;
    private Integer TIME_LIMIT = 120;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        //Inicia o serviço em primeiro plano
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this);
        notification.setSmallIcon(R.mipmap.ic_ds);
        notification.setContentTitle("MarkTv Socket");
        notification.setContentText("O aplicativo MarkTv Socket está em execução");
        notification.setContentIntent(pendingIntent);
        Notification notificationCompact = notification.build();
        startForeground(999, notificationCompact);

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        //Inicia o digital sigange
        startApp(DS_PACKAGE);

        //Inicia o socket
        startSocket();

        //Monitora o socket
        watchSocket();

        //Monitora e envia os logs
        Util.sendLogServer();

        return START_REDELIVER_INTENT;
    }

    /**
     * Inicia o Socket em segundo plano
     */
    private void startSocket() {
         serverThread = new Thread(new Runnable() {
            @Override
            public void run() {

                //Abre a porta
                try {
                    serverSocket = new ServerSocket(PORT);
                    Log.i(LOG_TAG, "Porta " + serverSocket.getLocalPort() + " aberta");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Aguarda as conexões dos clientes
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        String message = "Conectado com " + socket.getInetAddress() + ":" + socket.getPort();
                        socket.close();
                        DS_SLEEP = 0;
                        Log.i(LOG_TAG, message);
                        sendResponse(message);
                    } catch (Exception e) {
                        Log.i(LOG_TAG, "Erro na aplicação: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        });
        serverThread.setName("MarkTv Socket Server");
        serverThread.start();
    }

    /**
     * Monitora o socket
     */
    private void watchSocket() {
        final Service service = this;
        watchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while( !Thread.currentThread().isInterrupted() ) {

                    try {
                        Thread.sleep(30000);

                        //Verifica se o DigitalSignage está em execução
                        Boolean dsRunning = Util.appIsRunning(service, DS_PACKAGE);
                        if( dsRunning ) {
                            Util.log("DIGITAL SIGNAGE ONLINE");

                            DS_SLEEP += 30;
                            String msg;

                            //Verifica se o socket está aberto
                            if (serverSocket.isBound()) {
                                //Online
                                msg = "Socket Online, Porta: " + serverSocket.getLocalPort();
                            } else {
                                //Offline
                                msg = "Socket Offline, Porta: " + serverSocket.getLocalPort();
                                serverThread.interrupt();
                                //Reconecta
                                startSocket();
                            }
                            msg += ", Último ping: " + DS_SLEEP + "s";
                            Log.i(LOG_TAG, msg);
                            sendResponse(msg);

                            //Se o tempo se espera for longo reinicia o DigitalSignage
                            if (DS_SLEEP > TIME_LIMIT) {
                                DS_SLEEP = 0;
                                restartDS();
                            }
                        } else {
                            Util.log("DIGITAL SIGNAGE OFFLINE");
                        }

                    } catch (InterruptedException e) {
                        //e.printStackTrace();
                        Log.i(LOG_TAG, "Erro no Sleep: " + e.toString());
                    }
                }
            }
        });

        watchThread.setName("MarkTv Socket Watch");
        watchThread.start();
    }

    /**
     * Inicia um aplicativo
     */
    void startApp(String pkg) {
        log("Iniciando app: " + pkg);
        Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
        startActivity(LaunchIntent);
    }

    /**
     * Encerra um aplicativo
     */
    void killApp(String pkg) {
        log("Encerrando app: " + pkg);
        Process suProcess = null;
        try {
            suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
            os.writeBytes("adb shell" + "\n");
            os.flush();
            os.writeBytes("am force-stop " + pkg + "\n");
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reinicia o DigitalSignage
     */
    private void restartDS() {
        try {
            //Limite em 20 reboots
            if( totalRestart < 20 ) {
                killApp(DS_PACKAGE);
                Thread.sleep(10000);
                startApp(DS_PACKAGE);

                //Registra o log
                Util.addLogServer(Global.LOG_TIPO_DS_OFF, "O Digital Signage foi reiniciado");
                totalRestart++;
            } else {

                //Encerra o serviço
                Util.log("Encerrando o SocketService");
                killService();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Registra uma mensagem de log
     */
    void log(String msg) {
        Log.i(LOG_TAG, msg);
        sendResponse(msg);
    }

    /**
     * Encerra a aplicação
     */
    public void killService() {
        destroyAll();
        sendResponse("finish");
        killApp(SELF_PACKAGE);
        stopSelf();
    }

    /**
     * Envia mensagens para a aplicação
     * @param message
     */
    public void sendResponse(String message) {
        LocalBroadcastManager broadcaster = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent("SOCKET_SERVICE_RESPONSE");
        if( message != null ) {
            intent.putExtra("SOCKET_SERVICE_MSG", message);
        }
        broadcaster.sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        destroyAll();
        super.onDestroy();
    }


    /**
     * Encerra as threads
     */
    private void destroyAll() {
        if( serverThread != null ) {
            serverThread.interrupt();
        }
        if( watchThread != null ) {
            watchThread.interrupt();
        }
        if( Util.threadSendLog != null ) {
            Util.threadSendLog.interrupt();
        }
    }

}
