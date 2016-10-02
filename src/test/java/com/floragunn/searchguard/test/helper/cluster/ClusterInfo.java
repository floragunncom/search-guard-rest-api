package com.floragunn.searchguard.test.helper.cluster;

import java.util.HashSet;
import java.util.Set;

import org.elasticsearch.common.transport.InetSocketTransportAddress;

public class ClusterInfo {
	public int numNodes;
	public String httpHost = null;
	public int httpPort = -1;
	public Set<InetSocketTransportAddress> httpAdresses = new HashSet<InetSocketTransportAddress>();
	public String nodeHost;
	public int nodePort;
}
