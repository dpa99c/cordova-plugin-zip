package org.apache.cordova;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;

import android.net.Uri;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class Zip extends CordovaPlugin {

    private static final String LOG_TAG = "Zip";

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if ("unzip".equals(action)) {
            unzip(args, callbackContext);
            return true;
        }
        return false;
    }

    private void unzip(final CordovaArgs args, final CallbackContext callbackContext) {
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                unzipSync(args, callbackContext);
            }
        });
    }

    // Can't use DataInputStream because it has the wrong endian-ness.
    private static int readInt(InputStream is) throws IOException {
        int a = is.read();
        int b = is.read();
        int c = is.read();
        int d = is.read();
        return a | b << 8 | c << 16 | d << 24;
    }

    private void unzipSync(CordovaArgs args, CallbackContext callbackContext) {
        InputStream inputStream = null;
        try {
            String zipFileName = args.getString(0);
            String outputDirectory = args.getString(1);

            Uri zipUri = getUriForArg(zipFileName);
            Uri outputUri = getUriForArg(outputDirectory);
            CordovaResourceApi resourceApi = webView.getResourceApi();

            File tempFile = resourceApi.mapUriToFile(zipUri);
            if (tempFile == null || !tempFile.exists()) {
                sendError(callbackContext, "NO_ZIP_FILE", "Zip file does not exist");
                return;
            }

            File outputDir = resourceApi.mapUriToFile(outputUri);
            if (outputDir == null || (!outputDir.exists() && !outputDir.mkdirs())){
                sendError(callbackContext, "OUTPUT_DIR_ERROR", "Could not create output directory");
                return;
            }
            outputDirectory = outputDir.getAbsolutePath();
            outputDirectory += outputDirectory.endsWith(File.separator) ? "" : File.separator;

            OpenForReadResult zipFile = resourceApi.openForRead(zipUri);
            ProgressEvent progress = new ProgressEvent();
            progress.setTotal(zipFile.length);

            inputStream = new BufferedInputStream(zipFile.inputStream);
            inputStream.mark(10);
            int magic = readInt(inputStream);

            if (magic != 875721283) { // CRX identifier
                inputStream.reset();
            } else {
                // CRX files contain a header. This header consists of:
                //  * 4 bytes of magic number
                //  * 4 bytes of CRX format version,
                //  * 4 bytes of public key length
                //  * 4 bytes of signature length
                //  * the public key
                //  * the signature
                // and then the ordinary zip data follows. We skip over the header before creating the ZipInputStream.
                readInt(inputStream); // version == 2.
                int pubkeyLength = readInt(inputStream);
                int signatureLength = readInt(inputStream);

                inputStream.skip(pubkeyLength + signatureLength);
                progress.setLoaded(16 + pubkeyLength + signatureLength);
            }

            // The inputstream is now pointing at the start of the actual zip file content.
            ArchiveInputStream zis = new ArchiveStreamFactory().createArchiveInputStream("zip", inputStream);
            inputStream = zis;

            ZipArchiveEntry ze;
            byte[] buffer = new byte[32 * 1024];
            boolean anyEntries = false;

            while ((ze = (ZipArchiveEntry) zis.getNextEntry()) != null)
            {
                anyEntries = true;
                String compressedName = ze.getName();
                try {
                    if (ze.isDirectory()) {
                        File dir = new File(outputDirectory + compressedName);
                        if (!dir.exists() && !dir.mkdirs()) {
                            sendError(callbackContext, "OUTPUT_DIR_ERROR", "Could not create output directory for entry: " + compressedName);
                            return;
                        }
                    } else {
                        File file = new File(outputDirectory + compressedName);
                        File parent = file.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            sendError(callbackContext, "OUTPUT_DIR_ERROR", "Could not create parent directory for file: " + compressedName);
                            return;
                        }
                        if(file.exists() || file.createNewFile()){
                            Log.w("Zip", "extracting: " + file.getPath());
                            FileOutputStream fout = null;
                            try {
                                fout = new FileOutputStream(file);
                                int count;
                                while ((count = zis.read(buffer)) != -1)
                                {
                                    fout.write(buffer, 0, count);
                                }
                            } catch (IOException ioex) {
                                if (ioex.getMessage() != null && ioex.getMessage().contains("ENOSPC")) {
                                    sendError(callbackContext, "OUT_OF_STORAGE", "Out of storage space");
                                } else {
                                    sendError(callbackContext, "UNKNOWN_ERROR", ioex.getMessage());
                                }
                                return;
                            } finally {
                                if (fout != null) fout.close();
                            }
                        } else {
                            sendError(callbackContext, "OUTPUT_DIR_ERROR", "Could not create file: " + compressedName);
                            return;
                        }
                    }
                } catch (IOException ioex) {
                    if (ioex.getMessage() != null && ioex.getMessage().contains("ENOSPC")) {
                        sendError(callbackContext, "OUT_OF_STORAGE", "Out of storage space");
                    } else {
                        sendError(callbackContext, "UNKNOWN_ERROR", ioex.getMessage());
                    }
                    return;
                }
                progress.addLoaded(ze.getCompressedSize());
                updateProgress(callbackContext, progress);
            }

            // final progress = 100%
            progress.setLoaded(progress.getTotal());
            updateProgress(callbackContext, progress);

            if (anyEntries)
                callbackContext.success();
            else
                sendError(callbackContext, "BAD_ZIP_FILE", "Bad zip file");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "An error occurred while unzipping.";
            if (msg.contains("ENOSPC")) {
                sendError(callbackContext, "OUT_OF_STORAGE", "Out of storage space");
            } else {
                sendError(callbackContext, "UNKNOWN_ERROR", msg);
            }
            Log.e(LOG_TAG, msg, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void sendError(CallbackContext callbackContext, String code, String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            callbackContext.error(error);
        } catch (JSONException e) {
            callbackContext.error(message);
        }
    }

    private void updateProgress(CallbackContext callbackContext, ProgressEvent progress) throws JSONException {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, progress.toJSONObject());
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
    }

    private Uri getUriForArg(String arg) {
        CordovaResourceApi resourceApi = webView.getResourceApi();
        Uri tmpTarget = Uri.parse(arg);
        return resourceApi.remapUri(
                tmpTarget.getScheme() != null ? tmpTarget : Uri.fromFile(new File(arg)));
    }

    private static class ProgressEvent {
        private long loaded;
        private long total;
        public long getLoaded() {
            return loaded;
        }
        public void setLoaded(long loaded) {
            this.loaded = loaded;
        }
        public void addLoaded(long add) {
            this.loaded += add;
        }
        public long getTotal() {
            return total;
        }
        public void setTotal(long total) {
            this.total = total;
        }
        public JSONObject toJSONObject() throws JSONException {
            return new JSONObject(
                    "{loaded:" + loaded +
                    ",total:" + total + "}");
        }
    }
}
