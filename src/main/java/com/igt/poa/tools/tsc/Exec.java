package com.igt.poa.tools.tsc;

import com.jcraft.jsch.*;

import javax.swing.*;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Exec {

    static java.util.logging.Logger log = Logger.getLogger(Exec.class.getSimpleName());

    public static void main(String[] arg) {
        String host = null;
        if (arg.length > 0) {
            host = arg[0];
        } else {
            host = JOptionPane.showInputDialog("Enter username@hostname",
                    System.getProperty("user.name") +
                            "@localhost");
        }
        String user = host.substring(0, host.indexOf('@'));
        host = host.substring(host.indexOf('@') + 1);

        // username and password will be given via UserInfo interface.
        UserInfo ui = new PromptUserInfo();

        String command = JOptionPane.showInputDialog("Enter command",
                "set|grep SSH");
        //TODO: to truncate a remote file on linux: command="cat /dev/null > filename"

        String result = exec(user, ui, host, command);

        if (result != null) {
            JOptionPane.showMessageDialog(null, result);
        }
    }

    public static String exec(String user, UserInfo ui, String host, String command) {
        try {
            JSch jsch = new JSch();

            Session session = jsch.getSession(user, host, 22);

      /*
      String xhost="127.0.0.1";
      int xport=0;
      String display=JOptionPane.showInputDialog("Enter display name",
                                                 xhost+":"+xport);
      xhost=display.substring(0, display.indexOf(':'));
      xport=Integer.parseInt(display.substring(display.indexOf(':')+1));
      session.setX11Host(xhost);
      session.setX11Port(xport+6000);
      */

            session.setUserInfo(ui);
            session.connect();

            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // X Forwarding
            // channel.setXForwarding(true);

            //channel.setInputStream(System.in);
            channel.setInputStream(null);

            //channel.setOutputStream(System.out);

            //FileOutputStream fos=new FileOutputStream("/tmp/stderr");
            //((ChannelExec)channel).setErrStream(fos);
            ((ChannelExec) channel).setErrStream(System.err);

            InputStream in = channel.getInputStream();

            channel.connect();

            StringBuffer sb = new StringBuffer();
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    sb.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    log.info("exit-status: " + channel.getExitStatus());
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ee) {
                }
            }
            channel.disconnect();
            session.disconnect();

            return sb.toString();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Remote execution failed", e);
            return null;
        }
    }
}
