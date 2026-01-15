package com.izhan.seeterm;

import com.jcraft.jsch.*;
import com.google.common.base.Strings;
import org.apache.commons.io.FileUtils;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import javax.swing.*;
import java.io.*;
import java.net.URL;

public class Main {

    private static SSHInfo sshInfo;
    private static Session session;
    private static ChannelShell channel;
    private static OutputStream sshIn;
    private static WebEngine webEngine;

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            try {

                File webDir = WebResourceManager.copyWebResources();

                JFrame frame = new JFrame("Seeterm - SSH Client");
                frame.setSize(1000, 600);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                JFXPanel fxPanel = new JFXPanel();
                frame.setContentPane(fxPanel);

                Platform.runLater(() -> {

                    WebView webView = new WebView();
                    webEngine = webView.getEngine();

                    try {
                        File htmlFile = new File(webDir, "html/index.html");
                        webEngine.load(htmlFile.toURI().toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        JSObject window = (JSObject) webEngine.executeScript("window");
                        window.setMember("javaBridge", new JavaBridge());
                    });

                    fxPanel.setScene(new Scene(webView));
                });

                frame.setVisible(true);

            } catch (IOException e) {
                e.printStackTrace();
            }

        });

    }

    public static class JavaBridge {

        public void send(String data) {
            if (sshIn != null) {
                try {
                    sshIn.write(data.getBytes());
                    sshIn.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void setSSHInfo(String server, int port) {
            if (Strings.isNullOrEmpty(server)) return;
            sshInfo = new SSHInfo(server, port > 0 ? port : 22);
            startSSH();
        }
    }

    private static void startSSH() {

        new Thread(() -> {

            try {

                if (sshInfo == null) return;

                JSch jsch = new JSch();
                session = jsch.getSession("dummy", sshInfo.getServer(), sshInfo.getPort());
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                channel = (ChannelShell) session.openChannel("shell");

                PipedOutputStream toSSH = new PipedOutputStream();
                PipedInputStream fromJS = new PipedInputStream(toSSH);

                sshIn = channel.getOutputStream();
                channel.setInputStream(fromJS);

                InputStream sshOut = channel.getInputStream();
                channel.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(sshOut));
                String line;

                while ((line = reader.readLine()) != null) {
                    String finalLine = line + "\r\n";
                    Platform.runLater(() -> {
                        if (webEngine != null)
                            webEngine.executeScript("term.write(" + toJSString(finalLine) + ");");
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();

    }

    private static String toJSString(String str) {
        return "\"" + str.replace("\\", "\\\\")
                         .replace("\"", "\\\"")
                         .replace("\n", "\\n")
                         .replace("\r", "") + "\"";
    }

    public static class WebResourceManager {

        public static File copyWebResources() throws IOException {

            String os = System.getProperty("os.name").toLowerCase();
            File targetDir;

            if (os.contains("win")) targetDir = new File(System.getenv("APPDATA"), "Seeterm/web");
            else targetDir = new File(System.getProperty("user.home"), ".seeterm/web");

            if (!targetDir.exists()) targetDir.mkdirs();

            String[] resources = {"html", "css", "js"};

            for (String folder : resources) {
                ClassLoader classLoader = WebResourceManager.class.getClassLoader();
                URL resource = classLoader.getResource("web/" + folder);
                if (resource != null) {
                    File source = new File(resource.getPath());
                    FileUtils.copyDirectory(source, new File(targetDir, folder));
                }
            }

            return targetDir;
        }
    }
}
