package in.hridayan.ashell.shell;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import in.hridayan.ashell.utils.Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WifiAdbShell {

  private static List<String> mOutput;
  private static String mCommand;
  private static volatile boolean isMonitoring = false;
  private static Thread monitoringThread;

  private static Process mProcess = null;
  private static final int TIMEOUT = 750; // in milliseconds

  public WifiAdbShell(List<String> output, String command) {
    mOutput = output;
    mCommand = command;
  }

    // executes the commands send to adb
  public static void execCommand(Context context) {
    try {
      ProcessBuilder processBuilder;
      boolean useShellPrefix = shouldUseShellPrefix(mCommand);
      String[] commandArray =
          useShellPrefix ? new String[] {"shell", mCommand} : mCommand.split(" ");

      String[] fullCommand = new String[commandArray.length + 1];
      fullCommand[0] = adbPath(context);
      System.arraycopy(commandArray, 0, fullCommand, 1, commandArray.length);

      processBuilder = new ProcessBuilder(fullCommand);

      // Fix tmp dir issue
      String tmpDir = ensureTmpDir(context);
      Map<String, String> env = processBuilder.environment();
      env.put("HOME", context.getFilesDir().getAbsolutePath());
      env.put("ADB_VENDOR_KEYS", context.getFilesDir().getAbsolutePath());
      env.put("TMPDIR", tmpDir);

      mProcess = processBuilder.start();
      BufferedReader mInput = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
      BufferedReader mError = new BufferedReader(new InputStreamReader(mProcess.getErrorStream()));

      String line;
      while ((line = mInput.readLine()) != null) mOutput.add(line);
      while ((line = mError.readLine()) != null)
        mOutput.add("<font color=#FF0000>" + line + "</font>");

      int exitCode = mProcess.waitFor();
      mOutput.add("Process exited with code: " + exitCode);

    } catch (Exception e) {
      mOutput.add("<font color=#FF0000>Exception: " + e.getMessage() + "</font>");
    }
  }

  // Ensure tmp directory exists and return its path
  private static String ensureTmpDir(Context context) {
    File tmpDir = new File(context.getFilesDir(), "tmp");
    if (!tmpDir.exists()) {
      boolean created = tmpDir.mkdirs();
      Log.d("WifiAdbShell", "TMPDIR created: " + created);
    }
    return tmpDir.getAbsolutePath();
  }

  public interface PairingCallback {
    void onSuccess();

    void onFailure(String errorMessage);
  }

    // pair the device using ip, port and pairing code
  public static void pair(
      Context context, String ip, String port, String pairingCode, PairingCallback callback) {
    new Thread(
            () -> {
              try {
                // Kill any existing ADB server before pairing
                killServer(context);
                Thread.sleep(500); // Short delay before starting again

                ProcessBuilder processBuilder =
                    new ProcessBuilder(adbPath(context), "pair", ip + ":" + port, pairingCode);
                String tmpDir = ensureTmpDir(context);
                Map<String, String> env = processBuilder.environment();
                env.put("HOME", context.getFilesDir().getAbsolutePath());
                env.put("ADB_VENDOR_KEYS", context.getFilesDir().getAbsolutePath());
                env.put("TMPDIR", tmpDir); // Set TMPDIR to a writable path

                Process pairingProcess = processBuilder.start();

                BufferedReader reader =
                    new BufferedReader(new InputStreamReader(pairingProcess.getInputStream()));
                BufferedReader errorReader =
                    new BufferedReader(new InputStreamReader(pairingProcess.getErrorStream()));

                String line;
                boolean isSuccess = false;
                StringBuilder errorMessage = new StringBuilder();

                // Read ADB output
                while ((line = reader.readLine()) != null) {
                  if (line.contains("Successfully paired")) {
                    isSuccess = true;
                    break;
                  }
                }

                while ((line = errorReader.readLine()) != null) {
                  errorMessage.append(line).append("\n");
                }

                // Timeout check (if it takes too long)
                pairingProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                pairingProcess.destroy();

                boolean finalSuccess = isSuccess;
                String finalErrorMessage = errorMessage.toString().trim();

                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (finalSuccess) {
                            if (callback != null) callback.onSuccess();
                          } else {
                            if (callback != null)
                              callback.onFailure(
                                  finalErrorMessage.isEmpty()
                                      ? "Pairing failed"
                                      : finalErrorMessage);
                          }
                        });

              } catch (Exception e) {
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (callback != null) callback.onFailure(e.getMessage());
                        });
              }
            })
        .start();
  }

  public interface ConnectingCallback {
    void onSuccess();

    void onFailure(String errorMessage);
  }

    // connect the device using the ip and port
  public static void connect(Context context, String ip, String port, ConnectingCallback callback) {
    new Thread(
            () -> { // Run in background thread
              try {
                ProcessBuilder processBuilder =
                    new ProcessBuilder(adbPath(context), "connect", ip + ":" + port);

                String tmpDir = ensureTmpDir(context);
                Map<String, String> env = processBuilder.environment();
                env.put("HOME", context.getFilesDir().getAbsolutePath());
                env.put("ADB_VENDOR_KEYS", context.getFilesDir().getAbsolutePath());
                env.put("TMPDIR", tmpDir); // Set TMPDIR to a writable path

                Process connectingProcess = processBuilder.start();
                BufferedReader reader =
                    new BufferedReader(new InputStreamReader(connectingProcess.getInputStream()));
                BufferedReader errorReader =
                    new BufferedReader(new InputStreamReader(connectingProcess.getErrorStream()));

                String line;
                boolean isSuccess = false;
                StringBuilder errorMessage = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                  if (line.contains("connected to")) { // Check for success message
                    isSuccess = true;
                    break;
                  }
                }

                while ((line = errorReader.readLine()) != null) {
                  errorMessage.append(line).append("\n");
                }

                connectingProcess.waitFor(); // Wait for process to finish

                boolean finalSuccess = isSuccess;
                String finalErrorMessage = errorMessage.toString().trim();

                // Run callback on main thread
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          // finalSuccess = device connected
                          if (finalSuccess) {
                            if (callback != null) callback.onSuccess();
                          } else {
                            if (callback != null) callback.onFailure(finalErrorMessage);
                          }
                        });

              } catch (Exception e) {
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (callback != null) callback.onFailure(e.getMessage());
                        });
              }
            })
        .start();
  }

  public interface ConnectedDevicesCallback {
    void onDevicesListed(String devices);

    void onFailure(String errorMessage);
  }

  public static void getConnectedDevices(Context context, ConnectedDevicesCallback callback) {
    new Thread(
            () -> {
              try {

                ProcessBuilder processBuilder = new ProcessBuilder(adbPath(context), "devices");
                String tmpDir = ensureTmpDir(context);
                Map<String, String> env = processBuilder.environment();
                env.put("HOME", context.getFilesDir().getAbsolutePath());
                env.put("ADB_VENDOR_KEYS", context.getFilesDir().getAbsolutePath());
                env.put("TMPDIR", tmpDir); // Set TMPDIR to a writable path

                Process listProcess = processBuilder.start();

                BufferedReader reader =
                    new BufferedReader(new InputStreamReader(listProcess.getInputStream()));
                BufferedReader errorReader =
                    new BufferedReader(new InputStreamReader(listProcess.getErrorStream()));

                StringBuilder devicesList = new StringBuilder();

                // Read the first line (header) and ignore it
                reader.readLine();

                String currentLine;
                while ((currentLine = reader.readLine()) != null) {
                  if (!currentLine.trim().isEmpty()) {
                    devicesList.append(currentLine).append("\n"); // Append each device to the list
                  }
                }

                // Capture errors if any
                StringBuilder errorMessage = new StringBuilder();
                while ((currentLine = errorReader.readLine()) != null) {
                  errorMessage.append(currentLine).append("\n");
                }

                listProcess.waitFor(); // Wait for process to finish

                String finalDeviceList = devicesList.toString().trim();
                String finalErrorMessage = errorMessage.toString().trim();

                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (!finalDeviceList.isEmpty()) {
                            if (callback != null) callback.onDevicesListed(finalDeviceList);
                          } else {
                            if (callback != null)
                              callback.onFailure(
                                  finalErrorMessage.isEmpty()
                                      ? "No devices connected"
                                      : finalErrorMessage);
                          }
                        });

              } catch (Exception e) {
                new Handler(Looper.getMainLooper())
                    .post(
                        () -> {
                          if (callback != null) callback.onFailure(e.getMessage());
                        });
              }
            })
        .start();
  }

  public static String exec(Context context, String... command) {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      String tmpDir = ensureTmpDir(context);
      Map<String, String> env = processBuilder.environment();
      env.put("HOME", context.getFilesDir().getAbsolutePath());
      env.put("ADB_VENDOR_KEYS", context.getFilesDir().getAbsolutePath());
      env.put("TMPDIR", tmpDir); // Set TMPDIR to a writable path

      Process process = processBuilder.start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      BufferedReader errorReader =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));

      StringBuilder output = new StringBuilder();
      String line;

      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }
      while ((line = errorReader.readLine()) != null) {
        output.append("ERROR: ").append(line).append("\n");
      }

      process.waitFor();
      return output.toString().trim();
    } catch (Exception e) {
      return "Exception: " + e.getMessage();
    }
  }

  // Restart the adb server
  public static void restartAdbServer(Context context) {
    exec(context, "kill-server");
    exec(context, "start-server");
  }

  // Starts the adb tcpip server
  public static void startServer(Context context) {
    exec(context, "start-server");
  }

  // Kills the adb tcpip server
  public static void killServer(Context context) {
    exec(context, "kill-server");
  }

  // Checks if shell is busy or not
  public static boolean isBusy() {
    return mOutput != null
        && mOutput.size() > 0
        && !mOutput.get(mOutput.size() - 1).equals(Utils.shellDeadError());
  }

  // Destroys the running shell process
  public static void destroy() {
    if (mProcess != null) mProcess.destroy();
  }

  // This function determines if certain commands should use shell prefix in the command
  private static boolean shouldUseShellPrefix(String command) {
    String[] shellCommands = {
      "pm",
      "settings",
      "svc",
      "dumpsys",
      "am",
      "cmd",
      "monkey",
      "input",
      "logcat",
      "getprop",
      "setprop",
      "top",
      "wm",
      "content",
      "uiautomator",
      "screencap",
      "screenrecord",
      "chmod",
      "chown",
      "ls",
      "cd",
      "df",
      "du",
      "cat",
      "grep",
      "ps",
      "kill",
      "log",
      "date",
      "id",
      "uptime",
      "reboot",
      "svc",
      "ime",
      "service",
      "ip",
      "ifconfig",
      "netcfg",
      "netstat"
    };

    for (String prefix : shellCommands) {
      if (command.trim().startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String adbPath(Context context) {
    return context.getApplicationInfo().nativeLibraryDir + "/libadb.so";
  }
}
