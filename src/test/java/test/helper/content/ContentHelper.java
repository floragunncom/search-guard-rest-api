package test.helper.content;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;

public class ContentHelper {

	public static XContentBuilder parseJsonContent(final String jsonContent) {
		try {
			return readXContent(new StringReader(jsonContent), XContentType.YAML);
		} catch (IOException e) {
			return null;
		}
	}
	
	public static XContentBuilder readXContent(final Reader reader, final XContentType xContentType) throws IOException {
		XContentParser parser = null;
		try {
			parser = XContentFactory.xContent(xContentType).createParser(reader);
			parser.nextToken();
			final XContentBuilder builder = XContentFactory.jsonBuilder();
			builder.copyCurrentStructure(parser);
			return builder;
		} finally {
			if (parser != null) {
				parser.close();
			}
		}
	}
}
