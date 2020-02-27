package com.igt.poa.tools.tsc;

import com.jcraft.jsch.*;

import java.awt.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScpFrom {

    static Logger log = Logger.getLogger(ScpFrom.class.getSimpleName());

    public static void main(String[] arg) {
        if (arg.length != 2) {
            System.err.println("usage: java ScpFrom user@remotehost:file1 file2");
            System.exit(-1);
        }

        String user = arg[0].substring(0, arg[0].indexOf('@'));
        arg[0] = arg[0].substring(arg[0].indexOf('@') + 1);
        String host = arg[0].substring(0, arg[0].indexOf(':'));
        String rfile = arg[0].substring(arg[0].indexOf(':') + 1);
        String lfile = arg[1];

        // username and password will be given via UserInfo interface.
        UserInfo ui = new PromptUserInfo();

        File localFile = scpFrom(user, ui, host, rfile, lfile);

        if (localFile != null && localFile.exists()) {
            try {
                Desktop.getDesktop().open(localFile);
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to open the file in editor", e);
            }
        }

        System.exit(0);
    }

    static File scpFrom(String user, UserInfo ui, String host, String rFile, String lFile) {
        FileOutputStream fos = null;
        try {
            String prefix = null;
            if (new File(lFile).isDirectory()) {
                prefix = lFile + File.separator;
            }

            File localFile = null;

            JSch jsch = new JSch();
            Session session = jsch.getSession(user, host, 22);

            session.setUserInfo(ui);
            session.connect();

            // exec 'scp -f rFile' remotely
            rFile = rFile.replace("'", "'\"'\"'");
            rFile = "'" + rFile + "'";
            String command = "scp -f " + rFile;
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            // get I/O streams for remote scp
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();

            channel.connect();

            byte[] buf = new byte[1024];

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            while (true) {
                int c = checkAck(in);
                if (c != 'C') {
                    break;
                }

                // read '0644 '
                in.read(buf, 0, 5);

                long filesize = 0L;
                while (true) {
                    if (in.read(buf, 0, 1) < 0) {
                        // error
                        break;
                    }
                    if (buf[0] == ' ') break;
                    filesize = filesize * 10L + (long) (buf[0] - '0');
                }

                String file = null;
                for (int i = 0; ; i++) {
                    in.read(buf, i, 1);
                    if (buf[i] == (byte) 0x0a) {
                        file = new String(buf, 0, i);
                        break;
                    }
                }

                log.info("filesize=" + filesize + ", file=" + file);
                localFile = new File(prefix == null ? lFile : prefix + file);

                // send '\0'
                buf[0] = 0;
                out.write(buf, 0, 1);
                out.flush();

                // read a content of lFile
                fos = new FileOutputStream(localFile);
                int foo;
                while (true) {
                    if (buf.length < filesize) foo = buf.length;
                    else foo = (int) filesize;
                    foo = in.read(buf, 0, foo);
                    if (foo < 0) {
                        // error
                        break;
                    }
                    fos.write(buf, 0, foo);
                    filesize -= foo;
                    if (filesize == 0L) break;
                }
                fos.close();
                fos = null;

                if (checkAck(in) != 0) {
                    throw new IOException("ACK failed");
                }

                // send '\0'
                buf[0] = 0;
                out.write(buf, 0, 1);
                out.flush();
            }

            session.disconnect();

            log.info("localFile; " + localFile.getCanonicalPath() + ", size=" + localFile.length());
            return localFile;
        } catch (Exception e) {
            log.log(Level.SEVERE, "File download failed", e);
            try {
                if (fos != null) fos.close();
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
