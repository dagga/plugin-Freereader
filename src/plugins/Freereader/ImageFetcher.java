package plugins.Freereader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

import freenet.support.Logger;

/**
 * ImageFetcher
 * Downloads the images referenced in feed entries so they can be inserted
 * along with the freesite, and rewrites the entries to use the local copies.
 *
 * @author nicolas
 */
public class ImageFetcher
{
	private static final int MAX_IMAGE_SIZE = 1024 * 1024;
	private static final int CONNECT_TIMEOUT = 15 * 1000;
	private static final int READ_TIMEOUT = 30 * 1000;
	private static final int MAX_REDIRECTS = 5;
	
	private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern SRC_ATTR = Pattern.compile("\\ssrc\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", Pattern.CASE_INSENSITIVE);

	/**
	 * URL -> filename, or null if the image couldn't be fetched.
	 */
	private final HashMap<String, String> fetched = new HashMap<String, String>();

	@SuppressWarnings("unchecked")
	public void processFeed(SyndFeed feed)
	{
		for(SyndEntry entry : (List<SyndEntry>)feed.getEntries()) {
			// relative image URLs are resolved against the entry link
			String base = entry.getLink() != null ? entry.getLink() : feed.getLink();
			processContent(entry.getDescription(), base);
			for(SyndContent content : (List<SyndContent>)entry.getContents())
				processContent(content, base);
		}
	}

	private void processContent(SyndContent content, String base)
	{
		if(content == null || content.getValue() == null)
			return;
		content.setValue(rewriteImages(content.getValue(), base));
	}

	private String rewriteImages(String html, String base)
	{
		Matcher tags = IMG_TAG.matcher(html);
		StringBuffer out = new StringBuffer();
		while(tags.find()) {
			String tag = tags.group();
			String replacement = tag;
			Matcher src = SRC_ATTR.matcher(tag);
			if(src.find()) {
				String url = src.group(1) != null ? src.group(1) : src.group(2) != null ? src.group(2) : src.group(3);
				String file = fetch(resolve(base, url.trim().replace("&amp;", "&")));
			}
			tags.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		tags.appendTail(out);
		return out.toString();
	}

	private static String resolve(String base, String url)
	{
		try {
			return base != null ? new URL(new URL(base), url).toString() : new URL(url).toString();
		}
		catch(MalformedURLException e) {
			return null;
		}
	}

	/**
	 * Returns the filename of the image in IMAGE_DIR, or null if it couldn't be fetched.
	 */
	private String fetch(String url)
	{
		if(url == null)
			return null;
		if(fetched.containsKey(url))
			return fetched.get(url);

		String file = null;
		try {
			byte[] data = download(url);
		}
		catch(IOException e) {
			Logger.normal(this, "Couldn't fetch image " + url + ": " + e.getMessage());
		}
		fetched.put(url, file);
		return file;
	}
	
	private byte[] download(String address) throws IOException
	{
		URL url = new URL(address);
		HttpURLConnection connection;
		for(int redirects = 0; ; ++redirects) {
			if(!url.getProtocol().equals("http") && !url.getProtocol().equals("https"))
				throw new IOException("unsupported protocol");
			
			connection = (HttpURLConnection)url.openConnection();
			connection.setConnectTimeout(CONNECT_TIMEOUT);
			connection.setReadTimeout(READ_TIMEOUT);
			// redirects are followed by hand, as HttpURLConnection doesn't follow http -> https
			connection.setInstanceFollowRedirects(false);
			
			int code = connection.getResponseCode();
			String location = connection.getHeaderField("Location");
			if(code < 300 || code >= 400 || location == null)
				break;
			connection.disconnect();
			if(redirects == MAX_REDIRECTS)
				throw new IOException("too many redirects");
			url = new URL(url, location);
		}
		
		try {
			if(connection.getResponseCode() != HttpURLConnection.HTTP_OK)
				throw new IOException("HTTP " + connection.getResponseCode());
			if(connection.getContentLength() > MAX_IMAGE_SIZE)
				throw new IOException("image too big");
			
			return read(connection.getInputStream());
		}
		finally {
			connection.disconnect();
		}
	}
	
	private static byte[] read(InputStream in) throws IOException
	{
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int n;
			while((n = in.read(buffer)) != -1) {
				out.write(buffer, 0, n);
				if(out.size() > MAX_IMAGE_SIZE)
					throw new IOException("image too big");
			}
			return out.toByteArray();
		}
		finally {
			in.close();
		}
	}
}
