package uk.org.taverna.scufl2.translator.t2flow.t23activities;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import uk.org.taverna.scufl2.api.configurations.Configuration;
import uk.org.taverna.scufl2.api.io.ReaderException;
import uk.org.taverna.scufl2.translator.t2flow.ParserState;
import uk.org.taverna.scufl2.translator.t2flow.T2FlowParser;
import uk.org.taverna.scufl2.translator.t2flow.defaultactivities.AbstractActivityParser;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.ConfigBean;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.HTTPHeaders;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.RESTConfig;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class RESTActivityParser extends AbstractActivityParser {

	private static final String ACTIVITY_XSD = "/uk/org/taverna/scufl2/translator/t2flow/xsd/restactivity.xsd";

	private static URI ravenURI = T2FlowParser.ravenURI
			.resolve("net.sf.taverna.t2.activities/rest-activity/");

	private static URI ravenUIURI = T2FlowParser.ravenURI
			.resolve("net.sf.taverna.t2.ui-activities/rest-activity/");


	private static String className = "net.sf.taverna.t2.activities.rest.RESTActivity";

	public static URI ACTIVITY_URI = URI
			.create("http://ns.taverna.org.uk/2010/activity/rest");

	public static URI HTTP_URI = URI.create("http://www.w3.org/2011/http#");
	public static URI HTTP_HEADERS_URI = URI.create("http://www.w3.org/2011/http-headers#");
	public static URI HTTP_METHODS_URI = URI.create("http://www.w3.org/2011/http-methods#");


	@Override
	public boolean canHandlePlugin(URI activityURI) {
		String activityUriStr = activityURI.toASCIIString();
		return ( activityUriStr.startsWith(ravenURI.toASCIIString()) ||
				 activityUriStr.startsWith(ravenUIURI.toASCIIString()) )
				&& activityUriStr.endsWith(className);
	}

	@Override
	public List<URI> getAdditionalSchemas() {
		URL restXsd = RESTActivityParser.class.getResource(ACTIVITY_XSD);
		try {
			return Arrays.asList(restXsd.toURI());
		} catch (Exception e) {
			throw new IllegalStateException("Can't find REST schema "
					+ restXsd);
		}
	}

	@Override
	public URI mapT2flowRavenIdToScufl2URI(URI t2flowActivity) {
		return ACTIVITY_URI;
	}

	@Override
	public Configuration parseConfiguration(T2FlowParser t2FlowParser,
			ConfigBean configBean, ParserState parserState) throws ReaderException {


		RESTConfig restConfig = unmarshallConfig(t2FlowParser, configBean,
					"xstream", RESTConfig.class);

		Configuration configuration = new Configuration();
		configuration.setParent(parserState.getCurrentProfile());
		parserState.setCurrentConfiguration(configuration);
		try {
		    
		    ObjectNode json = (ObjectNode)configuration.getJson();
		    
		    configuration.setType(ACTIVITY_URI.resolve("#Config"));

		    ObjectNode request = json.objectNode();
		    json.put("request", request);

		    String method = restConfig.getHttpMethod().toUpperCase();
		    request.put("httpMethod", method);
		    request.put("absoluteURITemplate", restConfig.getUrlSignature());
		    
		    ArrayNode headers = json.arrayNode();
		    request.put("headers", headers);

			if (restConfig.getAcceptsHeaderValue() != null && ! restConfig.getAcceptsHeaderValue().isEmpty()) {
			    ObjectNode accept = json.objectNode();
			    headers.add(accept);
			    accept.put("header", "Accept");
			    accept.put("value", restConfig.getAcceptsHeaderValue());
			}
            if (hasContent(method)) {
				if (restConfig.getContentTypeForUpdates() != null && ! restConfig.getContentTypeForUpdates().isEmpty()) {
				    ObjectNode accept = json.objectNode();
	                headers.add(accept);
	                accept.put("header", "Content-Type");
	                accept.put("value", restConfig.getContentTypeForUpdates());
				}
				if (restConfig.isSendHTTPExpectRequestHeader()) {
                    ObjectNode accept = json.objectNode();
                    headers.add(accept);
                    accept.put("header", "Expect");
                    accept.put("value", "100-Continue");
				}
			}
			if (restConfig.getOtherHTTPHeaders() != null && restConfig.getOtherHTTPHeaders().getList() != null) {
				for (HTTPHeaders.List list : restConfig.getOtherHTTPHeaders().getList()) {
					String fieldName = list.getContent().get(0).getValue();
					String fieldValue = list.getContent().get(1).getValue();

                    ObjectNode accept = json.objectNode();
                    headers.add(accept);
                    accept.put("header", fieldName);
                    accept.put("value", fieldValue);
				}
			}
			if (restConfig.getShowActualUrlPort() != null) {
			    json.put("showActualURLPort", restConfig.getShowActualUrlPort().booleanValue());
			}
            if (restConfig.getShowResponseHeadersPort() != null) {
                json.put("showResponseHeadersPort", restConfig.getShowResponseHeadersPort().booleanValue());
            }

			if (restConfig.isShowRedirectionOutputPort()) {
			    json.put("showRedirectionOutputPort", true);
			}
			if (restConfig.getEscapeParameters() != null && ! restConfig.getEscapeParameters()) {
	             json.put("escapeParameters", false);
			}
			if (restConfig.getOutgoingDataFormat() != null) {
			    json.put("outgoingDataFormat", restConfig.getOutgoingDataFormat());
			}
			return configuration;
		} finally {
			parserState.setCurrentConfiguration(null);
		}
	}

	private boolean hasContent(String methodName) {
		if (Arrays.asList("GET", "HEAD", "DELETE", "CONNECT").contains(methodName)) {
			return false;
		}
		// Most probably does have or could have content
		return true;
	}


}
