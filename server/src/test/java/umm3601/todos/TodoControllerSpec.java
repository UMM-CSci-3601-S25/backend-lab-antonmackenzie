package umm3601.todos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.validation.Validation;
import io.javalin.validation.Validator;

/**
 * Tests the logic of the TodoController
 *
 * @throws IOException
 */
// The tests here include a ton of "magic numbers" (numeric constants).
// It wasn't clear to me that giving all of them owners would actually
// help things. The fact that it wasn't obvious what to call some
// of them says a lot. Maybe what this ultimately means is that
// these tests can/should be restructured so the constants (there are
// also a lot of "magic strings" that Checkstyle doesn't actually
// flag as a problem) make more sense.
@SuppressWarnings({ "MagicNumber" })
class TodoControllerSpec {

  // An instance of the controller we're testing that is prepared in
  // `setupEach()`, and then exercised in the various tests below.
  private TodoController todoController;

  // A Mongo object ID that is initialized in `setupEach()` and used
  // in a few of the tests. It isn't used all that often, though,
  // which suggests that maybe we should extract the tests that
  // care about it into their own spec file?
  private ObjectId JimmysId;

  // The client and database that will be used
  // for all the tests in this spec file.
  private static MongoClient mongoClient;
  private static MongoDatabase db;

  // Used to translate between JSON and POJOs.
  private static JavalinJackson javalinJackson = new JavalinJackson();

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Todo>> TodoArrayListCaptor;

  @Captor
  private ArgumentCaptor<Todo> TodoCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  /**
   * Sets up (the connection to the) DB once; that connection and DB will
   * then be (re)used for all the tests, and closed in the `teardown()`
   * method. It's somewhat expensive to establish a connection to the
   * database, and there are usually limits to how many connections
   * a database will support at once. Limiting ourselves to a single
   * connection that will be shared across all the tests in this spec
   * file helps both speed things up and reduce the load on the DB
   * engine.
   */
  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    // Reset our mock context and argument captor (declared with Mockito
    // annotations @Mock and @Captor)
    MockitoAnnotations.openMocks(this);

    // Setup database
    MongoCollection<Document> TodoDocuments = db.getCollection("todos");
    TodoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
        new Document()
            .append("owner", "Chris")
            .append("status", false)
            .append("body", "In sunt ex non tempor cillum commodo amet incididunt anim qui commodo quis. Cillum non labore ex sint esse.")
            .append("category", "software design"));
    testTodos.add(
      new Document()
      .append("owner", "Fry")
      .append("status", true)
      .append("body", "Ullamco irure laborum magna dolor non. Anim occaecat adipisicing cillum eu magna in.")
      .append("category", "homework"));
    testTodos.add(
      new Document()
      .append("owner", "Jill")
      .append("status", false)
      .append("body", "this is a potatoman")
      .append("category", "beat villans"));


            // There are two examples of suppressing CheckStyle



    JimmysId = new ObjectId();
    Document Jimmy = new Document()
        .append("_id", JimmysId)
        .append("owner", "Jimmy")
        .append("status", false)
        .append("body", "Jimmy shall climb Mt. Everest and make a delicious pie")
        .append("category", "jimmy things");

    TodoDocuments.insertMany(testTodos);
    TodoDocuments.insertOne(Jimmy);

    todoController = new TodoController(db);
  }

  @Test
  void addsRoutes() {
    Javalin mockServer = mock(Javalin.class);
    todoController.addRoutes(mockServer);
    verify(mockServer, Mockito.atLeast(3)).get(any(), any());
    verify(mockServer, Mockito.atLeastOnce()).post(any(), any());
    verify(mockServer, Mockito.atLeastOnce()).delete(any(), any());
  }

  @Test
  void canGetAllTodos() throws IOException {
    // When something asks the (mocked) context for the queryParamMap,
    // it will return an empty map (since there are no query params in
    // this case where we want all Todos).
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    // Now, go ahead and ask the TodoController to getTodos
    // (which will, indeed, ask the context for its queryParamMap)
    todoController.getUsers(ctx);

    // We are going to capture an argument to a function, and the type of
    // that argument will be of type ArrayList<Todo> (we said so earlier
    // using a Mockito annotation like this):
    // @Captor
    // private ArgumentCaptor<ArrayList<Todo>> TodoArrayListCaptor;
    // We only want to declare that captor once and let the annotation
    // help us accomplish reassignment of the value for the captor
    // We reset the values of our annotated declarations using the command
    // `MockitoAnnotations.openMocks(this);` in our @BeforeEach

    // Specifically, we want to pay attention to the ArrayList<Todo> that
    // is passed as input when ctx.json is called --- what is the argument
    // that was passed? We capture it and can refer to it later.
    verify(ctx).json(TodoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Check that the database collection holds the same number of documents
    // as the size of the captured List<Todo>
    assertEquals(
        db.getCollection("todos").countDocuments(),
        TodoArrayListCaptor.getValue().size());

  }

  @Test
  void getOwnerByCategory()
  {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    todoController.getUsers(ctx);

  }
  @Test
  void getOwner() {
    String owner = "Chris";
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {owner}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.OWNER_KEY)).thenReturn(owner);

    Validation validation = new Validation();
    // The `AGE_KEY` should be name of the key whose value is being validated.
    // You can actually put whatever you want here, because it's only used in the generation
    // of testing error reports, but using the actually key value will make those reports more informative.
    Validator<String> validator = validation.validator(TodoController.OWNER_KEY, String.class, owner);
    // When the code being tested calls `ctx.queryParamAsClass("age", Integer.class)`
    // we'll return the `Validator` we just constructed.
    when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class))
        .thenReturn(validator);

    todoController.getUsers(ctx);

    // Confirm that the code being tested calls `ctx.json(…)`, and capture whatever
    // is passed in as the argument when `ctx.json()` is called.
    verify(ctx).json(TodoArrayListCaptor.capture());
    // Confirm that the code under test calls `ctx.status(HttpStatus.OK)` is called.
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we get back two users.
    assertEquals(1, TodoArrayListCaptor.getValue().size());
    // Confirm that both users have age 37.
    for (Todo todo : TodoArrayListCaptor.getValue()) {
      assertEquals(owner, todo.owner);
    }
    // Generate a list of the names of the returned users.


  }

  @Test
  void getStatus() {
    String status = "complete";
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(status));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(status);

    Validation validation = new Validation();
    // The `AGE_KEY` should be name of the key whose value is being validated.
    // You can actually put whatever you want here, because it's only used in the generation
    // of testing error reports, but using the actually key value will make those reports more informative.
    Validator<String> validator = validation.validator(TodoController.STATUS_KEY, String.class, status);
    // When the code being tested calls `ctx.queryParamAsClass("age", Integer.class)`
    // we'll return the `Validator` we just constructed.
    when(ctx.queryParamAsClass(TodoController.STATUS_KEY, String.class))
        .thenReturn(validator);

    todoController.getUsers(ctx);


    // Confirm that the code being tested calls `ctx.json(…)`, and capture whatever
    // is passed in as the argument when `ctx.json()` is called.
    verify(ctx).json(TodoArrayListCaptor.capture());
    // Confirm that the code under test calls `ctx.status(HttpStatus.OK)` is called.
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we get back two users.
    assertEquals(1, TodoArrayListCaptor.getValue().size());
    // Confirm that both users have age 37.
    for (Todo todo : TodoArrayListCaptor.getValue()) {
      String isComplete = "false";
      if(todo.status = true)
      {
        isComplete = "complete";
      }
      assertEquals(status, isComplete);
    }
    // Generate a list of the names of the returned users.


  }


  @Test
  void getBody() {
    String containTarget = "potato";
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.CONTAINS_KEY, Arrays.asList(new String[] {containTarget}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CONTAINS_KEY)).thenReturn(containTarget);

    Validation validation = new Validation();
    // The `AGE_KEY` should be name of the key whose value is being validated.
    // You can actually put whatever you want here, because it's only used in the generation
    // of testing error reports, but using the actually key value will make those reports more informative.
    Validator<String> validator = validation.validator(TodoController.CONTAINS_KEY, String.class, containTarget);
    // When the code being tested calls `ctx.queryParamAsClass("age", Integer.class)`
    // we'll return the `Validator` we just constructed.
    when(ctx.queryParamAsClass(TodoController.CONTAINS_KEY, String.class))
        .thenReturn(validator);

    todoController.getUsers(ctx);

    // Confirm that the code being tested calls `ctx.json(…)`, and capture whatever
    // is passed in as the argument when `ctx.json()` is called.
    verify(ctx).json(TodoArrayListCaptor.capture());
    // Confirm that the code under test calls `ctx.status(HttpStatus.OK)` is called.
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we get back two users.
    assertEquals(1, TodoArrayListCaptor.getValue().size());
    // Confirm that both users have age 37.
    for (Todo todo : TodoArrayListCaptor.getValue()) {

      assertTrue(todo.body.contains(containTarget));
    }
    // Generate a list of the names of the returned users.


  }

  @Test
  void getCategory() {
    String targetCategory = "homework";
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(targetCategory));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn(targetCategory);

    Validation validation = new Validation();
    // The `AGE_KEY` should be name of the key whose value is being validated.
    // You can actually put whatever you want here, because it's only used in the generation
    // of testing error reports, but using the actually key value will make those reports more informative.
    Validator<String> validator = validation.validator(TodoController.CATEGORY_KEY, String.class, targetCategory);
    // When the code being tested calls `ctx.queryParamAsClass("age", Integer.class)`
    // we'll return the `Validator` we just constructed.
    when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class))
        .thenReturn(validator);

    todoController.getUsers(ctx);


    // Confirm that the code being tested calls `ctx.json(…)`, and capture whatever
    // is passed in as the argument when `ctx.json()` is called.
    verify(ctx).json(TodoArrayListCaptor.capture());
    // Confirm that the code under test calls `ctx.status(HttpStatus.OK)` is called.
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we get back two users.
    assertEquals(1, TodoArrayListCaptor.getValue().size());
    // Confirm that both users have age 37.
    for (Todo todo : TodoArrayListCaptor.getValue()) {

      assertEquals(targetCategory, todo.category);
    }
    // Generate a list of the names of the returned users.


  }

  @Test
  void getLimit() {
    Integer intTargetCategory = 2;
    String targetCategory = intTargetCategory.toString();
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.LIMIT_KEY, Arrays.asList(new String[] {targetCategory}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.LIMIT_KEY)).thenReturn(targetCategory);

    Validation validation = new Validation();
    // The `AGE_KEY` should be name of the key whose value is being validated.
    // You can actually put whatever you want here, because it's only used in the generation
    // of testing error reports, but using the actually key value will make those reports more informative.
    Validator<Integer> validator = validation.validator(TodoController.LIMIT_KEY, Integer.class, targetCategory);
    // When the code being tested calls `ctx.queryParamAsClass("age", Integer.class)`
    // we'll return the `Validator` we just constructed.
    when(ctx.queryParamAsClass(TodoController.LIMIT_KEY, Integer.class))
        .thenReturn(validator);

    todoController.getUsers(ctx);


    // Confirm that the code being tested calls `ctx.json(…)`, and capture whatever
    // is passed in as the argument when `ctx.json()` is called.
    verify(ctx).json(TodoArrayListCaptor.capture());
    // Confirm that the code under test calls `ctx.status(HttpStatus.OK)` is called.
    verify(ctx).status(HttpStatus.OK);

    // Confirm that we get back two users.
    assertEquals(2, TodoArrayListCaptor.getValue().size());

    // Generate a list of the names of the returned users.


  }

}
