package br.com.marktv.marktvsocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.io.IOException;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private BroadcastReceiver receiver;
    private TextView info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Global.mainActivity = this;
        setContentView(R.layout.activity_main);
        info = (TextView) findViewById(R.id.info);

        //Verifica se o serviço já está ativo
        Boolean portOpen = false;
        portOpen = checkPort(SocketService.PORT);

        //Inicia o serviço que abre a porta
        if( !portOpen ) {
            Intent it = new Intent("SOCKET_SERVICE");
            startService(it);

            //Recebe respostas do serviço
            serviceResponce();
        }
    }

    /**
     * Recebe respostas do serviço
     */
    protected void serviceResponce() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String msg = intent.getStringExtra("SOCKET_SERVICE_MSG");
                if( msg == "finish" ) {
                    finish();
                }
                info.setText(msg);
            }
        };
    }

    /**
     * Verifica se a porta está aberta
     */
    protected boolean checkPort(final int port) {
        final boolean[] isOpen = {false};
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                try {
                    socket = new Socket("localhost", port);
                    if( socket.isBound() ) {
                        isOpen[0] = true;
                    }
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            }
        });
        thread.setName("MarkTv Socket - checkPort");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return isOpen[0];
    }

    @Override
    protected void onStart() {
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver), new IntentFilter("SOCKET_SERVICE_RESPONSE"));
        super.onStart();
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }
}
