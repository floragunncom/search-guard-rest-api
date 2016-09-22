package test.helper.file;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

import test.AbstractSGUnitTest;

public class FileHelper {

	protected final static ESLogger log = Loggers.getLogger(FileHelper.class);

	public static File getAbsoluteFilePathFromClassPath(final String fileNameFromClasspath) {
		File file = null;
		final URL fileUrl = AbstractSGUnitTest.class.getClassLoader().getResource(fileNameFromClasspath);
		if (fileUrl != null) {
			try {
				file = new File(URLDecoder.decode(fileUrl.getFile(), "UTF-8"));
			} catch (final UnsupportedEncodingException e) {
				return null;
			}

			if (file.exists() && file.canRead()) {
				return file;
			} else {
				log.error("Cannot read from {}, maybe the file does not exists? ", file.getAbsolutePath());
			}

		} else {
			log.error("Failed to load " + fileNameFromClasspath);
		}
		return null;
	}

	public static final String loadFile(final String file) throws IOException {
		final StringWriter sw = new StringWriter();
		IOUtils.copy(FileHelper.class.getResourceAsStream("/" + file), sw, StandardCharsets.UTF_8);
		return sw.toString();
	}

	public static BytesReference readYamlContent(final String file) {
		try {
			return readXContent(new StringReader(loadFile(file)), XContentType.YAML);
		} catch (IOException e) {
			return null;
		}
	}

	public static BytesReference readXContent(final Reader reader, final XContentType xContentType) throws IOException {
		XContentParser parser = null;
		try {
			parser = XContentFactory.xContent(xContentType).createParser(reader);
			parser.nextToken();
			final XContentBuilder builder = XContentFactory.jsonBuilder();
			builder.copyCurrentStructure(parser);
			return builder.bytes();
		} finally {
			if (parser != null) {
				parser.close();
			}
		}
	}
}
