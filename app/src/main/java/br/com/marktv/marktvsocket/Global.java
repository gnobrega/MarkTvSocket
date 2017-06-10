package br.com.marktv.marktvsocket;

import android.os.Environment;

import java.io.File;

/**
 * Created by Gustavo on 30/05/2017.
 */

public class Global {
    public static MainActivity mainActivity;
    public static File sdcard = Environment.getExternalStorageDirectory();
    public static String pathFileIni = sdcard.getAbsolutePath() + "/wikipix.ini";
    public static String LOG_FILE = "MarkTvSocket";
    public static String serverHost = "http://gestor.wikipix.com.br";
    public static String serverLogUrl = serverHost + "/player-log/registrar";
    public static Integer LOG_TIPO_DS_OFF = 1;
    public static String SELF_PACKAGE = "br.com.marktv.marktvsocket";
}
