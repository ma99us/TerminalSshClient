package com.igt.poa.tools.tsc;

import com.jcraft.jsch.*;

import javax.swing.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScpTo {

    static java.util.logging.Logger log = Logger.getLogger(ScpTo.class.getSimpleName());

    public static void main(String[] arg) {
        if (arg.length != 2) {
            System.err.println("usage: java ScpTo file1 user@remotehost:file2");
            System.exit(-1);
        }

        String lfile = arg[0];
        String user = arg[1].substring(0, arg[1].indexOf('@'));
        arg[1] = arg[1].substring(arg[1].indexOf('@') + 1);
        String host = arg[1].substring(0, arg[1].indexOf(':'));
        String rfile = arg[1].substring(arg[1].indexOf(':') + 1);

        // username and password will be given via UserInfo interface.
        UserInfo ui = new PromptUserInfo();

        Integer sent = scpTo(user, ui, host, lfile, rfile);

        if(sent != null && sent > 0) {
            JOptionPane.showMessageDialog(null, "Bytes sent: " + sent);
        }
    }

    public static Integer scpTo(String user, UserInfo ui, String host, String lFile, String rFile) {
        FileInputStream fis = null;
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);

            session.setUserInfo(ui);
            session.connect();

            boolean ptimestamp = true;

            // exec 'scp -t rfile' remotely
            rFile = rFile.replace("'", "'\"'\"'");
            rFile = "'" + rFile + "'";
            String command = "scp " + (ptimestamp ? "-p" : "") + " -t " + rFile;
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            if (checkAck(in) != 0) {
                throw new IOException("ACK failed");
            }

            File _lfile = new File(lFile);

            if (ptimestamp) {
                command = "T" + (_lfile.lastModified() / 1000) + " 0"; //FIXME: space or no space after "T" ?
                // The access time should be sent here,
                // but it is not accessible with JavaAPI ;-<
                command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
                out.write(command.getBytes());
                out.flush();
                if (checkAck(in) != 0) {
                    //throw new IOException("ACK failed");
                    log.warning("ACK failed; can not set remote file timestamp");
                }
            }

            // send "C0644 filesize filename", where filename should not include '/'
            long filesize = _lfile.length();
            command = "C0644 " + filesize + " ";
            if (lFile.lastIndexOf('/') > 0) {
                command += lFile.substring(lFile.lastIndexOf('/') + 1);
            } else {
                command += lFile;
            }
            command += "\n";
            out.write(command.getBytes());
            out.flush();
            if (checkAck(in) != 0) {
                throw new IOException("ACK failed");
            }

            // send a content of lFile
            fis = new FileInputStream(lFile);
            byte[] buf = new byte[1024];
            int totalBytesSent = 0;
            while (true) {
                int len = fis.read(buf, 0, buf.length);
                if (len <= 0) break;
                out.write(buf, 0, len); //out.flush();
                totalBytesSent += len;
            }
            fis.close();
            fis = null;
            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();
            if (checkAck(in) != 0) {
                throw new IOException("ACK failed");
            }
            out.close();

            channel.disconnect();
            session.disconnect();

            return totalBytesSent;
        } catch (Exception e) {
            log.log(Level.SEVERE, "File upload failed", e);
            try {
                if (fis != null) fis.close();
            } catch (Exception ee) {
            }
            return null;
        }
    }

    static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            }
            while (c != '\n');
            if (b == 1) { // error
                log.info("error: " + sb.toString());
            }
            if (b == 2) { // fatal error
                log.info("fatal error: " + sb.toString());
            }
        }
        return b;
    }
}
