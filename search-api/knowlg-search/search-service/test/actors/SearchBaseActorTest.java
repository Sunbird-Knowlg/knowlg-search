package actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.*;
import org.sunbird.common.JsonUtils;
import org.sunbird.common.Platform;
import org.sunbird.common.dto.Request;
import org.sunbird.common.dto.Response;
import org.sunbird.common.dto.ResponseParams;
import org.sunbird.common.exception.ResponseCode;
import org.sunbird.common.exception.ServerException;
import org.sunbird.search.client.ElasticSearchUtil;
import org.sunbird.search.util.SearchConstants;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import actors.SearchActor;
import actors.AuditHistoryActor;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class SearchBaseActorTest {

    protected static ActorSystem system = null;
    protected static final String SEARCH_ACTOR = "SearchActor";
    protected static final String AUDIT_HISTORY_ACTOR = "AuditHistoryActor";
    private static String[] keywords = new String[]{"hindi story", "NCERT", "Pratham", "एकस्टेप", "हिन्दी", "हाथी और भालू", "worksheet", "test"};
    private static String[] contentTypes = new String[]{"Resource", "Collection", "Asset"};
    private static String[] ageGroup = new String[]{"<5","5-6", "6-7", "7-8","8-10",">10","Other"};
    private static String[] gradeLevel = new String[]{"Kindergarten","Class 1", "Class 2", "Class 3", "Class 4","Class 5","Other"};
    
    @BeforeClass
    public static void beforeTest() throws Exception {
        system = ActorSystem.create();
        
    }


    protected Request getSearchRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, SEARCH_ACTOR , "INDEX_SEARCH");
    }

    protected Request getCountRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, SEARCH_ACTOR , "COUNT");
    }

    protected Request getMetricsRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, SEARCH_ACTOR , "METRICS");
    }

    protected Request getAuditRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>());
        return setSearchContext(request, AUDIT_HISTORY_ACTOR , "SEARCH_OPERATION_AND");
    }

    protected Request getGroupSearchResultsRequest() {
        Request request = new Request();
        return setSearchContext(request, SEARCH_ACTOR , "GROUP_SEARCH_RESULT_BY_OBJECTTYPE");
    }

    protected Request setSearchContext(Request request, String manager, String operation) {
        request.setOperation(operation);
        return request;
    }
    
    protected Response getResponse(Request request, Props props){
        try{
            ActorRef searchMgr = system.actorOf(props);
            Future<Object> future = Patterns.ask(searchMgr, request, 30000);
            Object obj = Await.result(future, Duration.create(30.0, TimeUnit.SECONDS));
            Response response = null;
            if (obj instanceof Response) {
                response = (Response) obj;
            } else {
                response = ERROR(SearchConstants.SYSTEM_ERROR, "System Error", ResponseCode.SERVER_ERROR);
            }
            return response;
        } catch (Exception e) {
            throw new ServerException(SearchConstants.SYSTEM_ERROR, e.getMessage(), e);
        }
    }

    protected Response getSearchResponse(Request request) {
        final Props props = Props.create(SearchActor.class);
        return getResponse(request, props);
    }

    protected Response getAuditResponse(Request request) {
        final Props props = Props.create(AuditHistoryActor.class);
        return getResponse(request, props);
    }

    protected Response ERROR(String errorCode, String errorMessage, ResponseCode responseCode) {
        Response response = new Response();
        response.setParams(getErrorStatus(errorCode, errorMessage));
        response.setResponseCode(responseCode);
        return response;
    }

    private ResponseParams getErrorStatus(String errorCode, String errorMessage) {
        ResponseParams params = new ResponseParams();
        params.setErr(errorCode);
        params.setStatus(ResponseParams.StatusType.failed.name());
        params.setErrmsg(errorMessage);
        return params;
    }

    protected static void createCompositeSearchIndex() throws Exception {
        SearchConstants.COMPOSITE_SEARCH_INDEX = "testcompositeindex";
        ElasticSearchUtil.initialiseESClient(SearchConstants.COMPOSITE_SEARCH_INDEX,
                Platform.config.getString("search.es_conn_info"));
        System.out.println("creating index: " + SearchConstants.COMPOSITE_SEARCH_INDEX);
        String settings = "{\"analysis\": {       \"analyzer\": {         \"cs_index_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"lowercase\",             \"mynGram\"           ]         },         \"cs_search_analyzer\": {           \"type\": \"custom\",           \"tokenizer\": \"standard\",           \"filter\": [             \"standard\",             \"lowercase\"           ]         },         \"keylower\": {           \"tokenizer\": \"keyword\",           \"filter\": \"lowercase\"         }       },       \"filter\": {         \"mynGram\": {           \"type\": \"nGram\",           \"min_gram\": 1,           \"max_gram\": 20,           \"token_chars\": [             \"letter\",             \"digit\",             \"whitespace\",             \"punctuation\",             \"symbol\"           ]         }       }     }   }";
        String mappings = "{\"dynamic_templates\":[{\"longs\":{\"match_mapping_type\":\"long\",\"mapping\":{\"type\":\"long\",\"fields\":{\"raw\":{\"type\":\"long\"}}}}},{\"booleans\":{\"match_mapping_type\":\"boolean\",\"mapping\":{\"type\":\"boolean\",\"fields\":{\"raw\":{\"type\":\"boolean\"}}}}},{\"doubles\":{\"match_mapping_type\":\"double\",\"mapping\":{\"type\":\"double\",\"fields\":{\"raw\":{\"type\":\"double\"}}}}},{\"dates\":{\"match_mapping_type\":\"date\",\"mapping\":{\"type\":\"date\",\"fields\":{\"raw\":{\"type\":\"date\"}}}}},{\"strings\":{\"match_mapping_type\":\"string\",\"mapping\":{\"type\":\"text\",\"copy_to\":\"all_fields\",\"analyzer\":\"cs_index_analyzer\",\"search_analyzer\":\"cs_search_analyzer\",\"fields\":{\"raw\":{\"type\":\"text\",\"fielddata\":\"true\",\"analyzer\":\"keylower\"}}}}}],\"properties\":{\"all_fields\":{\"type\":\"text\",\"analyzer\":\"cs_index_analyzer\",\"search_analyzer\":\"cs_search_analyzer\",\"fields\":{\"raw\":{\"type\":\"text\",\"fielddata\":\"true\",\"analyzer\":\"keylower\"}}}}}";
        ElasticSearchUtil.addIndex(SearchConstants.COMPOSITE_SEARCH_INDEX,
                SearchConstants.COMPOSITE_SEARCH_INDEX_TYPE, settings, mappings);
        insertTestRecords();
    }

    private static void insertTestRecords() throws Exception {
        for (int i=1; i<=30; i++) {
            Map<String, Object> content = getContentTestRecord(null, i, null);
            String id = (String) content.get("identifier");
            addToIndex(id, content);
        }
        Map<String, Object> content = getContentTestRecord("do_10000031", 31, null);
        content.put("name", "31 check name match");
        content.put("description", "हिन्दी description");
        content.put("subject", Arrays.asList("English", "Hindi"));
        content.put("medium", Arrays.asList("English", "Hindi"));
        addToIndex("do_10000031", content);

        content = getContentTestRecord("do_10000032", 32, null);
        content.put("name", "check ends with value32");
        addToIndex("do_10000032", content);

        content = getContentTestRecord("do_10000033", 33, "test-board1");
        content.put("name", "Content To Test Consumption");
        addToIndex("10000033", content);

        content = getContentTestRecord("do_10000034", 34, "test-board3");
        content.put("name", "Textbook-10000034");
        content.put("description", "Textbook for other tenant");
        content.put("status","Live");
        content.put("subject", Arrays.asList("Maths", "Science"));
        content.put("medium", Arrays.asList("English", "Hindi"));
        content.put("relatedBoards", new ArrayList<String>(){{
            add("test-board1");
            add("test-board2");
        }});
        addToIndex("10000034", content);

        content = getContentTestRecord("do_10000035", 35, "test-board4");
        content.put("name", "Test Course - TrainingCourse");
        content.put("description", "Test Course - TrainingCourse");
        content.put("status","Live");
        content.put("mimeType", "application/vnd.ekstep.content-collection");
        content.put("contentType", "Course");
        content.put("courseType", "TrainingCourse");
        addToIndex("10000035", content);
    }

    private static void addToIndex(String uniqueId, Map<String, Object> doc) throws Exception {
        String jsonIndexDocument = JsonUtils.serialize(doc);
        ElasticSearchUtil.addDocumentWithId(SearchConstants.COMPOSITE_SEARCH_INDEX,
                SearchConstants.COMPOSITE_SEARCH_INDEX_TYPE, uniqueId, jsonIndexDocument);
    }


    protected static void createAuditIndex() throws Exception {
        SearchConstants.COMPOSITE_SEARCH_INDEX = "testauditindex";
        ElasticSearchUtil.initialiseESClient(SearchConstants.COMPOSITE_SEARCH_INDEX,
                Platform.config.getString("search.es_conn_info"));
        System.out.println("creating index: " + SearchConstants.COMPOSITE_SEARCH_INDEX);
        String settings = "{\"analysis\": {\"filter\": {\"mynGram\": {\"token_chars\": [\"letter\", \"digit\", \"whitespace\", \"punctuation\", \"symbol\"], \"min_gram\": \"1\", \"type\": \"nGram\", \"max_gram\": \"20\"}}, \"analyzer\": {\"ah_search_analyzer\": {\"filter\": [\"standard\", \"lowercase\"], \"type\": \"custom\", \"tokenizer\": \"standard\"}, \"keylower\": {\"filter\": \"lowercase\", \"tokenizer\": \"keyword\"}, \"ah_index_analyzer\": {\"filter\": [\"lowercase\", \"mynGram\"], \"type\": \"custom\", \"tokenizer\": \"standard\"}}}}";
        String mappings = "{\"dynamic_templates\": [{\"longs\": {\"mapping\": {\"type\": \"long\", \"fields\": {\"raw\": {\"type\": \"long\"}}}, \"match_mapping_type\": \"long\"}}, {\"booleans\": {\"mapping\": {\"type\": \"boolean\", \"fields\": {\"raw\": {\"type\": \"boolean\"}}}, \"match_mapping_type\": \"boolean\"}}, {\"doubles\": {\"mapping\": {\"type\": \"double\", \"fields\": {\"raw\": {\"type\": \"double\"}}}, \"match_mapping_type\": \"double\"}}, {\"dates\": {\"mapping\": {\"type\": \"date\", \"fields\": {\"raw\": {\"type\": \"date\"}}}, \"match_mapping_type\": \"date\"}}, {\"strings\": {\"mapping\": {\"type\": \"text\", \"copy_to\": \"all_fields\", \"analyzer\": \"ah_index_analyzer\", \"search_analyzer\": \"ah_search_analyzer\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"match_mapping_type\": \"string\"}}], \"properties\": {\"@timestamp\": {\"type\": \"date\", \"fields\": {\"raw\": {\"type\": \"date\", \"format\": \"strict_date_optional_time||epoch_millis\"}}}, \"@version\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"all_fields\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"audit_id\": {\"type\": \"long\", \"fields\": {\"raw\": {\"type\": \"long\"}}}, \"createdOn\": {\"type\": \"date\", \"fields\": {\"raw\": {\"type\": \"date\", \"format\": \"strict_date_optional_time||epoch_millis\"}}}, \"graphId\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"label\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"logRecord\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"objectId\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"objectType\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"operation\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"requestId\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"summary\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}, \"userId\": {\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"text\", \"fielddata\": true, \"analyzer\": \"keylower\"}}}}}";
        ElasticSearchUtil.addIndex(SearchConstants.COMPOSITE_SEARCH_INDEX, SearchConstants.AUDIT_HISTORY_INDEX_TYPE, settings, mappings);
        insertAuditLogRecords(SearchConstants.COMPOSITE_SEARCH_INDEX, SearchConstants.AUDIT_HISTORY_INDEX_TYPE);
    }

    private static void insertAuditLogRecords(String indexName, String indexType) throws Exception {

        Map<String, Object> record1 = getAuditRecord("1234", "Content", "", "domain", "ANONYMOUS", "", "{\"properties\":{\"IL_FUNC_OBJECT_TYPE\":{\"nv\":\"Content\"},\"IL_UNIQUE_ID\":{\"nv\":\"1234\"}}}", "CREATE", 1687396483000L);
        addToIndex(indexName, indexType, "VD9N54gBVD187cnp9Nmo", record1);

        Map<String, Object> record2 = getAuditRecord("1234", "Content", "", "domain", "ANONYMOUS", "", "{\"properties\":{\"IL_FUNC_OBJECT_TYPE\":{\"nv\":\"Content\"},\"IL_UNIQUE_ID\":{\"nv\":\"1234\"}}}", "CREATE", 1687396483000L);
        addToIndex(indexName, indexType, "YT-a54gBVD187cnpEtl_", record2);

        Map<String, Object> record3 = getAuditRecord("1234", "Content", "", "domain", "ANONYMOUS", "", "{\"properties\":{\"IL_FUNC_OBJECT_TYPE\":{\"nv\":\"Content\"},\"IL_UNIQUE_ID\":{\"nv\":\"1234\"}}}", "CREATE", 1687396483000L);
        addToIndex(indexName, indexType, "VT9N54gBVD187cnp-Nlc", record3);

        Map<String, Object> record4 = getAuditRecord("1234", "Content", "", "domain", "ANONYMOUS", "", "{\"properties\":{\"name\":{\"nv\":\"new name\"},\"status\":{\"nv\":\"Live\"}}}", "UPDATE", 1687396488000L);
        addToIndex(indexName, indexType, "Vz9O54gBVD187cnpQdmx", record4);

        Map<String, Object> record5 = getAuditRecord("do_113807000868651008130", "Content", "", "domain", "ANONYMOUS", "", "invalidLogRecord", "CREATE", 1687397870000L);
        addToIndex(indexName, indexType, "WT9O54gBVD187cnpf9nQ", record5);

        Map<String, Object> record6 = getAuditRecord("do_113807000868651008131", "Content", "", "domain", "ANONYMOUS", "", "", "UPDATE", 1687396488000L);
        addToIndex(indexName, indexType, "Vz9O54gBVD187cnpQdmx", record6);
    }

    private static void addToIndex(String indexName, String indexType, String uniqueId, Map<String, Object> doc) throws Exception {
        String jsonIndexDocument = JsonUtils.serialize(doc);
        ElasticSearchUtil.addDocumentWithId(indexName, indexType, uniqueId, jsonIndexDocument);
    }

    private static Map<String, Object> getAuditRecord(String objectId, String objectType, String label, String graphId, String userId, String requestId, String logRecord, String operation, long createdOn) {
        Map<String, Object> record = new HashMap<>();
        record.put("objectId", objectId);
        record.put("objectType", objectType);
        record.put("label", label);
        record.put("graphId", graphId);
        record.put("userId", userId);
        record.put("requestId", requestId);
        record.put("logRecord", logRecord);
        record.put("operation", operation);
        record.put("createdOn", createdOn);
        return record;
    }

    private static Map<String, Object> getContentTestRecord(String id, int index, String board) {
        String objectType = "Content";
        Date d = new Date();
        Map<String, Object> map = getTestRecord(id, index, "do", objectType);
        map.put("name", "Content_" + System.currentTimeMillis() + "_name");
        map.put("code", "code_" + System.currentTimeMillis());
        map.put("contentType", getContentType());
        map.put("createdOn", new Date().toString());
        map.put("lastUpdatedOn", new Date().toString());
        if(StringUtils.isNotBlank(board))
            map.put("board",board);
        Set<String> ageList = getAgeGroup();
        if (null != ageList && !ageList.isEmpty())
            map.put("ageGroup", ageList);
        Set<String> grades = getGradeLevel();
        if (null != grades && !grades.isEmpty())
            map.put("gradeLevel", grades);
        if (index % 5 == 0) {
            map.put("lastPublishedOn", d.toString());
            map.put("status", "Live");
            map.put("size", 1000432);
        } else {
            map.put("status", "Draft");
            if (index % 3 == 0)
                map.put("size", 564738);
        }
        Set<String> tagList = getTags();
        if (null != tagList && !tagList.isEmpty() && index % 7 != 0)
            map.put("keywords", tagList);
        map.put("downloads", index);
        map.put("visibility", "Default");
        return map;
    }

    private static Map<String, Object> getTestRecord(String id, int index, String prefix, String objectType) {
        Map<String, Object> map = new HashMap<String, Object>();
        if (StringUtils.isNotBlank(id))
            map.put("identifier", id);
        else {
            long suffix = 10000000 + index;
            map.put("identifier", prefix + "_" + suffix);
        }
        map.put("objectType", objectType);
        return map;
    }

    private static String getContentType() {
        return contentTypes[RandomUtils.nextInt(3)];
    }

    private static Set<String> getTags() {
        Set<String> list = new HashSet<String>();
        int count = RandomUtils.nextInt(9);
        for (int i=0; i<count; i++) {
            list.add(keywords[RandomUtils.nextInt(8)]);
        }
        return list;
    }

    private static Set<String> getAgeGroup() {
        Set<String> list = new HashSet<String>();
        int count = RandomUtils.nextInt(2);
        for (int i=0; i<count; i++) {
            list.add(ageGroup[RandomUtils.nextInt(6)]);
        }
        return list;
    }
    
    private static Set<String> getGradeLevel() {
        Set<String> list = new HashSet<String>();
        int count = RandomUtils.nextInt(2);
        for (int i=0; i<count; i++) {
            list.add(gradeLevel[RandomUtils.nextInt(6)]);
        }
        return list;
    }
    
}
