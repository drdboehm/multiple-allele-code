/**
 * This file is part of project mac-client from the multiple-allele-code repository.
 *
 * mac-client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mac-client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mac-client.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.nmdp.b12s.mac.client.http;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Date;
import org.apache.http.HttpHost;

/**
 * Simple client that may be invoked from the command line.
 */
public class TextClientMain implements Closeable {

    private Operation operation;

    private String hlaDbVersion = null;

    private HttpAlleleCodeService macService;

    private HttpHost proxyHost = null;

    public TextClientMain(String baseurl) {
        macService = new HttpAlleleCodeService(baseurl);
        // set expand operation as default
        setOperationToExpand();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: [--proxy=<proxyurl:port>] --url=<baseurl>] [--hla=<version>] ['expand'|'encode'] typings|files...");
            System.err.println("\tversion is the IMGT/HLA Release version like 3.19.0 or 3.22.0");
            System.err.println("Sample args:\t  --url=https://hml.nmdp.org/mac/api --hla=3.22.0 expand HLA-A*01:MN");
            System.exit(1);
        }
        int pos = 0;
        try (TextClientMain client = new TextClientMain("https://hml.nmdp.org/mac/api")) {
            while (pos < args.length) {
                String arg = args[pos++];
                File f = new File(arg);
                if (arg.startsWith("--hla=")) {
                    client.setHlaDbVersion(arg.substring(arg.indexOf('=') + 1));
                } else if (arg.startsWith("--proxy=")) {
                    String proxy = arg.substring(arg.indexOf('=') + 1);
                    System.out.println(proxy);
                    int index;
                    String protocol = null;
                    String url;
                    Integer port;

                    if ((index = proxy.indexOf("://")) > -1) {
                        String[] parts = proxy.split("://");
                        protocol = parts[0];
                        proxy = parts[1];
                    }
                    if ((proxy.indexOf(":")) > -1) {
                        String[] parts = proxy.split(":");
                        url = parts[0];
                        port = Integer.parseInt(parts[1]);
                        client.proxyHost = new HttpHost(url, port, protocol);
                    }

                } else if (arg.startsWith("--url=")) {
                    client.setBaseUrl(arg.substring(arg.indexOf('=') + 1));
                } else if (arg.equals("expand")) {
                    client.setOperationToExpand();
                    System.out.println("switch to" + client.operation.getOperation());
                } else if (arg.equals("decode")) {
                    client.setOperationToDecode();
                    System.out.println("switch to" + client.operation.getOperation());
                } else if (arg.equals("encode")) {
                    client.setOperationToEncode();
                    System.out.println("switch to: " + client.operation.getOperation());
                } else if (f.exists()) {
                    processFile(f, client.operation);
                } else {
                    String performed = client.operation.perform(arg);
                    System.out.println(arg + " " + client.operation.getOperation() + "s to " + performed);
                }
            }
        } finally {

        }
    }

    private String sendRequest(String typings) {
        System.out.println(typings);
        String textResponse = "";
        try {
            textResponse = macService.expand(hlaDbVersion, typings);
        } catch (IllegalArgumentException e) {
            // Notify user to correct input values.
            e.printStackTrace();
        } catch (RuntimeException e) {
            // System or network issue.  
            e.printStackTrace();
        }
        return textResponse;
    }

    private void setOperationToDecode() {
        this.operation = new Operation() {
            @Override
            public String perform(String inputValue) {
                return macService.decode(inputValue);
            }

            @Override
            public String getOperation() {
                return "'decode'";
            }
        };
    }

    interface Operation {

        String perform(String inputValue);

        String getOperation();
    }

    private void setOperationToExpand() {
        this.operation = new Operation() {
            @Override
            public String perform(String inputValue) {
                return macService.expand(hlaDbVersion, inputValue);
            }

            @Override
            public String getOperation() {
                return "'expand'";
            }
        };
    }

    private void setOperationToEncode() {
        this.operation = new Operation() {
            @Override
            public String perform(String inputValue) {
                return macService.encode(hlaDbVersion, inputValue);
            }

            @Override
            public String getOperation() {
                return "'encode'";
            }
        };
    }

    private static void processFile(File file, Operation operation) throws IOException {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
        LineNumberReader lnr = new LineNumberReader(reader);
        String typing;
        int invalidAlleleCount = 0;
        int lineNumber = 0;
        while (null != (typing = lnr.readLine())) {
            lineNumber = lnr.getLineNumber();
//            if (typing.length() < 6 || typing.indexOf('/') > 0) {
            if (typing.length() < 16) {
                continue;// skip allele lists
            }
            try {
                String value = operation.perform(typing);
                if (value == null) {
                    System.out.println("NULL for : " + typing);
                } else {
                    System.out.printf("%2d = %s%n", lineNumber, value);
                }
            } catch (Exception re) {
                String message = re.toString();
                if (message.contains("Invalid allele")) {
                    invalidAlleleCount++;
                } else {
                    System.out.println("ERR :" + message
                            + "\n\t for typing: " + typing);
                }
            } finally {
                if (lineNumber % 200 == 0) {
                    System.out.println(lineNumber + "\t " + new Date());
                }
            }
        }
        System.out.println("lines= " + lineNumber);
        System.out.println("invalid alleles= " + invalidAlleleCount);
        lnr.close();
    }

    @Override
    public void close() throws IOException {
        macService.close();
    }

    public String getHlaDbVersion() {
        return hlaDbVersion;
    }

    private void setBaseUrl(String baseurl) {
        try {
            macService.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (proxyHost == null) {
            macService = new HttpAlleleCodeService(baseurl);
        } else {
            macService = new HttpAlleleCodeService(baseurl, proxyHost);
        }
    }

    public void setHlaDbVersion(String hlaDbVersion) {
        this.hlaDbVersion = hlaDbVersion;
    }

}
