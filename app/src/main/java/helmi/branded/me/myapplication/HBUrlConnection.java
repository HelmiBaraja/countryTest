package helmi.branded.me.myapplication;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class HBUrlConnection {
	private static final String TAG = "HBUrlConnection";
	public boolean allowUntrustedHttps;
	public boolean syncrhonizeHttp;
	public String soapValue;
	/* delegate need to be set from caller class to be used */
	public HBUrlListener listener;
	private HBUrlConnection http = null;
	private OverlayTask task;
	private HttpURLConnection connection;
	private File file=null;
	private String fileKey;
	private String crlf = "\r\n";
	private String twoHyphens = "--";
	private String  boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
	private boolean isPostMethod;

	public HBUrlConnection() {
		listener = null;
		syncrhonizeHttp = false;
		allowUntrustedHttps = true;
		task = null;
		soapValue = null;
		isPostMethod= false;
	}

	/*
	 * Initialize Https
	 */

	public String convertToParams(Map<String, String> map) {
		String param = "";
		Set<String> keys = map.keySet();
		for (Iterator<String> i = keys.iterator(); i.hasNext();) {
			String key = (String) i.next();
			String value = (String) map.get(key);

			param += String.format("%s=%s", key, value, key);
			param += "&";
		}

		// delete last character ('&')
		param = param.substring(0, param.length() - 1);

		return param;
	}

	public void connectionWithSoapMessage(String url, String mode, Map<String, String> map) {
		map.put("method", mode);

		send(url, convertToParams(map));
	}

	public String sendGet(String targetURL)
	{
		isPostMethod = false;
		return send(targetURL, null);
	}

	public String sendPost(String targetURL, String urlParameters)
	{
		isPostMethod= true;
		return send(targetURL, urlParameters);
	}

	private String send(String targetURL, String urlParameters) {

		if (syncrhonizeHttp) {
			Log.i(TAG, "syncrhonizeHttp: " + syncrhonizeHttp);
			try {
				return startUrlConnection(targetURL, urlParameters);
			} catch (IOException e) {
				Log.e("error", "unable to send data Sync", e);
			}
		} else { // async
			Log.i(TAG, "syncrhonizeHttp: "+syncrhonizeHttp);
			if (listener == null) {
				Log.e("error", "listener can't be null");
				return null;
			}

			if (isConnecting())
				return null;

			task = new OverlayTask();
//			task.execute(targetURL, urlParameters);
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,targetURL, urlParameters);
		}
		return null;

	}

	private String startUrlConnection(String targetURL, String urlParameters) throws IOException {

		URL url;
		connection = null;
		String output = null;
		try {
			// Create connection
			url = new URL(targetURL);
			// trustAllHosts();

			if (allowUntrustedHttps)
				FakeSSL.allowAllSSL();

			System.setProperty("javax.net.debug", "all");
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(isPostMethod?"POST":"GET");

			if (soapValue != null)
				connection.setRequestProperty("SOAPAction", soapValue);


			connection.setUseCaches(false);
			connection.setDoInput(true);

			if (isPostMethod) {
				connection.setDoOutput(true);

				DataOutputStream wr = null;

				//if need to send File.
				if (this.file != null) {

					connection.setRequestProperty("Connection", "Keep-Alive");
					connection.setRequestProperty("Cache-Control", "no-cache");
					connection.setRequestProperty(
							"Content-Type", "multipart/form-data;boundary=" + boundary);

					wr = new DataOutputStream(connection.getOutputStream());

					String[] keyValue = urlParameters.split("&");
					for (int i = 0; i < keyValue.length; i++) {
						String key = keyValue[i].split("=")[0];
						String value = keyValue[i].split("=")[1];

						this.addFormField(wr, key, value);
					}

				} else {

					wr = new DataOutputStream(connection.getOutputStream());
					wr.writeBytes(urlParameters);
				}

				// Send request

				wr.flush();
				wr.close();
			}

			// Get Response
			InputStream is;

			Log.i(TAG, "Url:" + targetURL + "\nparam:" + urlParameters);

			if (connection.getResponseCode() <= 400) {
				is = connection.getInputStream();
			} else {
				/* error from server */
				is = connection.getErrorStream();
			}

			BufferedReader rd = new BufferedReader(new InputStreamReader(is));

			StringWriter sw = new StringWriter();
			char[] buffer = new char[1024 * 4];
			int n = 0;
			while (-1 != (n = rd.read(buffer))) {
				sw.write(buffer, 0, n);
			}
			output = sw.toString();

			Log.i(TAG, "Code: " + connection.getResponseCode());
			Log.i(TAG, "response: " + output);

		} finally {

			if (connection != null) {
				connection.disconnect();
			}
		}
		return output;
	}

	private void addFormField(DataOutputStream wr, String key, String value) throws IOException {

		wr.writeBytes(twoHyphens + boundary + crlf);
		wr.writeBytes("Content-Disposition: form-data; name=\"" +key + "\""+crlf);
		wr.writeBytes(crlf);
		wr.writeBytes(value+crlf);
		wr.writeBytes(crlf);
		wr.flush();
		Log.d("tag","added "+key+"="+value);

	}

	/* to cancel / stop / abort existing connection */
	public void abortConnection() {
		Log.d("abort", "connection");
		if (task != null)
			task.cancel(true);

		if (connection != null)
			connection.disconnect();
		connection = null;
	}

	/* check whether is there any existing connection is made */
	public boolean isConnecting() {
		if (http == null)
			return false;
		else
			return true;
	}

	/* Create interface to be used callback method to caller class */
	public interface HBUrlListener {
		void requestDidFinish(String output);

		void requestDidFail(String output);
	}

	private class OverlayTask extends AsyncTask<String, Void, String> {
//		String response;
		Exception error;

		@Override
		protected void onCancelled() {
			listener = null;
		}

		@Override
		protected String doInBackground(String... params) {
			try {
				String response = startUrlConnection(params[0], params[1]);

				return response;

				// convert any HTML tag to plain text
				// response = android.text.Html.fromHtml(response).toString();
			} catch (IOException e) {
				error = e;
				Log.e("error", "unable to send data Async", e);
			}

			return "";
		}

		@Override
		protected void onPostExecute(String response) {
			// callback method
			if (error != null || response == null)
				listener.requestDidFail(error.getLocalizedMessage());
			else if (listener != null)
				listener.requestDidFinish(response);
		}

	}
}