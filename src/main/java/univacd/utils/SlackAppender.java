package univacd.utils;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Plugin(name = "Slack", category = "Core", elementType = "appender", printObject = true)
public final class SlackAppender extends AbstractAppender {

	private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
	private final Lock readLock = rwLock.readLock();
	private String webHook;
	private String user;
	private String channel;
	private String hostname = "<unknow host>";
	private String encoding;

	protected SlackAppender(String name, Filter filter, Layout<? extends Serializable> layout, String webHook,
			String user, String channel, String encoding, final boolean ignoreExceptions) {
		super(name, filter, layout, ignoreExceptions);
		this.webHook = webHook;
		this.user = user;
		this.channel = channel;
		this.encoding = encoding;
		try {
			InetAddress addr;
			addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
		} catch (UnknownHostException ex) {
		}
	}

	@Override
	public void append(LogEvent event) {
		readLock.lock();
		try {
			post(event);
		} catch (Exception ex) {
			if (!ignoreExceptions()) {
				throw new AppenderLoggingException(ex);
			}
		} finally {
			readLock.unlock();
		}
	}

	@PluginFactory
	public static SlackAppender createAppender(@PluginAttribute("name") String name,
			@PluginElement("Layout") Layout<? extends Serializable> layout,
			@PluginElement("Filter") final Filter filter, @PluginAttribute("WebHook") String webHook,
			@PluginAttribute("user") String user, @PluginAttribute("channel") String channel,
			@PluginAttribute("encoding") String encoding) {
		if (name == null) {
			LOGGER.error("No name provided for SlackAppender");
			return null;
		}
		if (webHook == null) {
			LOGGER.error("No name provided for WebHook");
			return null;
		}
		if (layout == null) {
			layout = PatternLayout.createDefaultLayout();
		}
		return new SlackAppender(name, filter, layout, webHook, user, channel,
				encoding == null ? "UTF-8" : encoding, true);
	}

	private void post(LogEvent event) {
		try {
			URL obj = new URL(webHook);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			con.setRequestMethod("POST");
			con.setRequestProperty("Content-type", "application/json");

			Color color;
			switch (event.getLevel().getStandardLevel()) {
			case FATAL:
			case ERROR:
				color = Color.RED;
				break;
			case WARN:
				color = Color.ORANGE;
				break;
			case INFO:
				color = Color.BLACK;
				break;
			case DEBUG:
				color = Color.BLUE;
				break;
			case TRACE:
				color = Color.GREEN;
				break;
			default:
				color = Color.GRAY;
				break;
			}

			final byte[] bytes = getLayout().toByteArray(event);

			StringBuilder json = new StringBuilder("{");
			if (user != null) {
				json.append("\"userName\":\"");
				json.append(user);
				json.append("\",");

			}
			if (channel != null) {
				json.append("\"channel\":\"");
				json.append(channel);
				json.append("\",");

			}

			json.append("\"attachments\":[{");
			json.append("\"color\":\"#");
			json.append(Integer.toHexString(color.getRGB()).substring(2));
			json.append("\",\"title\":\"");
			json.append(formatToSlack(event.getLevel().name()));
			json.append(" on ");
			json.append(formatToSlack(hostname));
			json.append("\",\"text\":\"");
			json.append(formatToSlack(new String(bytes, encoding)));
			json.append("\"}]}");

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(json.toString());
			wr.flush();
			wr.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String line;
			StringBuffer response = new StringBuffer();

			while ((line = in.readLine()) != null) {
				response.append(line);
			}
			in.close();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static String formatToSlack(String text) {
		if (text == null) {
			return null;
		}
		return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");

	}

}