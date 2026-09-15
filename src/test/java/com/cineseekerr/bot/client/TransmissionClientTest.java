package com.cineseekerr.bot.client;

import com.cineseekerr.bot.config.CineSeekerrProperties;
import com.cineseekerr.bot.model.TransmissionTorrent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TransmissionClientTest {

    @Test
    void retriesTheRpcCallWithTransmissionSessionIdAndReturnsOnlyCompletedStates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TransmissionClient client = new TransmissionClient(builder, settings());

        server.expect(once(), requestTo("http://transmission:9091/transmission/rpc"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(CONFLICT)
                        .header("X-Transmission-Session-Id", "session-123"));
        server.expect(once(), requestTo("http://transmission:9091/transmission/rpc"))
                .andExpect(header("X-Transmission-Session-Id", "session-123"))
                .andExpect(jsonPath("$.method").value("torrent-get"))
                .andRespond(withSuccess("""
                        {"arguments":{"torrents":[
                          {"hashString":"DONE","name":"Completed","status":6,"percentDone":1.0,"totalSize":1073741824,"downloadDir":"/downloads/complete/movies"},
                          {"hashString":"STOPPED","name":"Stopped complete","status":0,"percentDone":1.0,"totalSize":100,"downloadDir":"/downloads/complete"},
                          {"hashString":"ACTIVE","name":"Active","status":4,"percentDone":0.5,"totalSize":2147483648,"downloadDir":"/downloads/incomplete"},
                          {"hashString":"ACTIVE100","name":"Active at 100%","status":4,"percentDone":1.0,"totalSize":2147483648,"downloadDir":"/downloads/complete/movies"},
                          {"hashString":"EMPTY","name":"Empty","status":6,"percentDone":1.0,"totalSize":0,"downloadDir":"/downloads/complete"}
                        ]},"result":"success"}
                        """, MediaType.APPLICATION_JSON));

        List<TransmissionTorrent> completed = client.cleanupCandidates(false);

        assertThat(completed).extracting(TransmissionTorrent::hashString)
                .containsExactly("DONE", "STOPPED");
        server.verify();
    }

    @Test
    void eliminaCandidatesIncludeIncompleteTorrentsInsideAllowedDownloadRoots() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TransmissionClient client = new TransmissionClient(builder, settings());

        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andRespond(withSuccess("""
                        {"arguments":{"torrents":[
                          {"hashString":"PARTIAL","name":"Partial","status":4,"percentDone":0.943,
                           "totalSize":2147483648,"downloadDir":"/downloads/complete/tv-sonarr"},
                          {"hashString":"DONE","name":"Completed","status":6,"percentDone":1.0,
                           "totalSize":1073741824,"downloadDir":"/downloads/complete/movies"},
                          {"hashString":"OUTSIDE","name":"Outside","status":4,"percentDone":0.5,
                           "totalSize":100,"downloadDir":"/Media/Movies"},
                          {"hashString":"EMPTY","name":"Empty","status":4,"percentDone":0.5,
                           "totalSize":0,"downloadDir":"/downloads/complete"}
                        ]},"result":"success"}
                        """, MediaType.APPLICATION_JSON));

        List<TransmissionTorrent> candidates = client.cleanupCandidates(true);

        assertThat(candidates).extracting(TransmissionTorrent::hashString)
                .containsExactly("PARTIAL", "DONE");
        server.verify();
    }

    @Test
    void eliminaRevalidatesAndCanRemoveASelectedIncompleteTorrent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TransmissionClient client = new TransmissionClient(builder, settings());

        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andRespond(withSuccess("""
                        {"arguments":{"torrents":[
                          {"hashString":"PARTIAL","name":"Partial","status":4,"percentDone":0.943,
                           "totalSize":2147483648,"downloadDir":"/downloads/complete/tv-sonarr"}
                        ]},"result":"success"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andExpect(jsonPath("$.method").value("torrent-remove"))
                .andExpect(jsonPath("$.arguments.ids[0]").value("PARTIAL"))
                .andExpect(jsonPath("$.arguments['delete-local-data']").value(true))
                .andRespond(withSuccess("{\"arguments\":{},\"result\":\"success\"}", MediaType.APPLICATION_JSON));

        List<TransmissionTorrent> removed = client.removeEligible(Set.of("PARTIAL"), true);

        assertThat(removed).extracting(TransmissionTorrent::hashString).containsExactly("PARTIAL");
        server.verify();
    }

    @Test
    void revalidatesSnapshotAndKeepsDataForClear() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TransmissionClient client = new TransmissionClient(builder, settings());

        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andExpect(jsonPath("$.method").value("torrent-get"))
                .andRespond(withSuccess(torrentsResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andExpect(jsonPath("$.method").value("torrent-remove"))
                .andExpect(jsonPath("$.arguments.ids[0]").value("DONE"))
                .andExpect(jsonPath("$.arguments['delete-local-data']").value(false))
                .andRespond(withSuccess("{\"arguments\":{},\"result\":\"success\"}", MediaType.APPLICATION_JSON));

        List<TransmissionTorrent> removed = client.removeEligible(Set.of("DONE", "NO_LONGER_PRESENT"), false);

        assertThat(removed).extracting(TransmissionTorrent::hashString).containsExactly("DONE");
        server.verify();
    }

    @Test
    void setsDeleteLocalDataOnlyForElimina() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TransmissionClient client = new TransmissionClient(builder, settings());

        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andRespond(withSuccess(torrentsResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andExpect(jsonPath("$.arguments['delete-local-data']").value(true))
                .andRespond(withSuccess("{\"arguments\":{},\"result\":\"success\"}", MediaType.APPLICATION_JSON));

        client.removeEligible(Set.of("DONE"), true);

        server.verify();
    }

    @Test
    void refusesDataDeletionOutsideConfiguredDownloadRoots() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TransmissionClient client = new TransmissionClient(builder, settings());

        server.expect(requestTo("http://transmission:9091/transmission/rpc"))
                .andRespond(withSuccess("""
                        {"arguments":{"torrents":[
                          {"hashString":"LIBRARY","name":"Library file","status":6,"percentDone":1.0,
                           "totalSize":1073741824,"downloadDir":"/Media/Movies"},
                          {"hashString":"TRAVERSAL","name":"Traversal","status":6,"percentDone":1.0,
                           "totalSize":1073741824,"downloadDir":"/downloads/complete/../../Media/Movies"}
                        ]},"result":"success"}
                        """, MediaType.APPLICATION_JSON));

        List<TransmissionTorrent> removed = client.removeEligible(
                Set.of("LIBRARY", "TRAVERSAL"), true);

        assertThat(removed).isEmpty();
        server.verify();
    }

    private static String torrentsResponse() {
        return "{\"arguments\":{\"torrents\":[{\"hashString\":\"DONE\",\"name\":\"Completed\",\"status\":6,\"percentDone\":1.0,\"totalSize\":1073741824,\"downloadDir\":\"/downloads/complete/movies\"}]},\"result\":\"success\"}";
    }

    private static CineSeekerrProperties.Transmission settings() {
        return new CineSeekerrProperties.Transmission(
                "http://transmission:9091/transmission/rpc", "user", "password",
                List.of("/downloads/complete"));
    }
}
