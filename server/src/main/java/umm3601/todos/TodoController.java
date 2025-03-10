package umm3601.todos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

/**
 * Controller that manages requests for info about users.
 */
public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";
  private static final String API_TODOS_BY_OID = "/api/todos/{$oid}";
  static final String OWNER_KEY = "owner";
  static final String STATUS_KEY = "status";
  static final String BODY_KEY = "body";
  static final String CATEGORY_KEY = "category";
  static final String LIMIT_KEY = "limit";
  static final String CONTAINS_KEY = "contains";
  private Todo[] allTodos;

  private final JacksonMongoCollection<Todo> todoCollection;

  /**
   * Construct a controller for users.
   *
   * @param database the database containing user data
   */
  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Set the JSON body of the response to be the single user
   * specified by the `id` parameter in the request
   *
   * @param ctx a Javalin HTTP context
   */
  public void getUser(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;

    try {
      todo = todoCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested user id wasn't a legal Mongo Object ID.");
    }
    if (todo == null) {
      throw new NotFoundResponse("The requested user was not found");
    } else {
      ctx.json(todo);
      ctx.status(HttpStatus.OK);
    }
  }

  /**
   * Set the JSON body of the response to be a list of all the users returned from the database
   * that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getUsers(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);

    // All three of the find, sort, and into steps happen "in parallel" inside the
    // database system. So MongoDB is going to find the users with the specified
    // properties, return those sorted in the specified manner, and put the
    // results into an initially empty ArrayList.
    ArrayList<Todo> matchingUsers = todoCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .limit(getLimit(ctx))
      .into(new ArrayList<>());

    // Set the JSON body of the response to be the list of users returned by the database.
    // According to the Javalin documentation (https://javalin.io/documentation#context),
    // this calls result(jsonString), and also sets content type to json
    ctx.json(matchingUsers);

    // Explicitly set the context status to OK
    ctx.status(HttpStatus.OK);
  }
  /**
   * Construct a Bson filter document to use in the `find` method based on the
   * query parameters from the context.
   *Ageresence of the `age`, `company`, and `role` query
   * parameters and constructs a filter document that will match users with
   * the specified values for those fields.
   *
   * @param ctx a Javalin HTTP context, which contains the query parameters
   *    used to construct the filter
   * @return a Bson filter document that can be used in the `find` method
   *   to filter the database collection of users
   */
  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>(); // start with an empty list of filters

    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {
      String category = ctx.queryParamAsClass(CATEGORY_KEY, String.class)
        .get();
      filters.add(eq(CATEGORY_KEY, category));
    }

    if (ctx.queryParamMap().containsKey(OWNER_KEY)) {
      String owner = ctx.queryParamAsClass(OWNER_KEY, String.class)
        .get();
      filters.add(eq(OWNER_KEY, owner));
    }

    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      String completeness = ctx.queryParamAsClass(STATUS_KEY, String.class)
      .check(it-> it.toLowerCase().equals("complete") || it.toLowerCase().equals("incomplete"), "Input does not query for complete or incomplete todos; Input provided: " + ctx.queryParam(STATUS_KEY))
      .get();
    filters.add(eq(STATUS_KEY, completeness.toLowerCase().equals("complete")));
    }
    if (ctx.queryParamMap().containsKey(CONTAINS_KEY)) {
      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(CONTAINS_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(BODY_KEY, pattern));
    }
    // Combine the list of filters into a single filtering document.
    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }


  /**
   * Construct a Bson sorting document to use in the `sort` method based on the
   * query parameters from the context.
   *
   * This checks for the presence of the `sortby` and `sortorder` query
   * parameters and constructs a sorting document that will sort users by
   * the specified field in the specified order. If the `sortby` query
   * parameter is not present, it defaults to "name". If the `sortorder`
   * query parameter is not present, it defaults to "asc".
   *
   * @param ctx a Javalin HTTP context, whic                                       ^h contains the query p"status": false,arameters
   *   used to construct the sorting order
   * @return a Bson sorting document that can be used in the `sort` method
   *  to sort the database collection of users
   */
  private int getLimit(Context ctx){
    if (ctx.queryParamMap().containsKey(LIMIT_KEY)){
      int todoLimit = ctx.queryParamAsClass(LIMIT_KEY, Integer.class)
        .check(it -> it > 0, "Todo's Limit must be greater than zero; you provided " + ctx.queryParam(LIMIT_KEY))
        .get();
        return todoLimit;
    }
    else{
      return (int)todoCollection.countDocuments();
    }
  }
  private Bson constructSortingOrder(Context ctx) {
    // Sort the results. Use the `sortby` query param (default "name")
    // as the field to sort by, and the query param `sortorder` (default
    // "asc") to specify the sort order.
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortby"), "name");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }

  /**
   * Set the JSON body of the response to be a list of all the user names and IDs
   * returned from the database, grouped by company
   *
   * This "returns" a list of user names and IDs, grouped by company in the JSON
   * body of the response. The user names and IDs are stored in `UserIdName` objects,
   * and the company name, the number of users in that company, and the list of user
   * names and IDs are stored in `UserByCompany` objects.
   *
   * @param ctx a Javalin HTTP context that provides the query parameters
   *   used to sort the results. We support either sorting by company name
   *   (in either `asc` or `desc` order) or by the number of users in the
   *   company (`count`, also in either `asc` or `desc` order).
   */

  public void getTodosGroupedByOwner(Context ctx)
  {
    // We'll support sorting the results either by company name (in either `asc` or `desc` order)
    // or by the number of users in the company (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "$oid");
    if (sortBy.equals("status")) {
      sortBy = "$oid";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    // The `UserByCompany` class is a simple class that has fields for the company
    // name, the number of users in that company, and a list of user names and IDs
    // (using the `UserIdName` class to store the user names and IDs).
    // We're going to use the aggregation pipeline to group users by company, and
    // then count the number of users in each company. We'll also collect the userserver/src/main/java/umm3601/user/UserIdName.java
    // names and IDs for each user in each company. We'll then convert the results
    // of the aggregation pipeline to `UserByCompany` objects.

    ArrayList<TodoByCategory> matchingTodos = todoCollection
      // The following aggregation pipeline groups users by company, and
      // then counts the number of users in each company. It also collects
      // the user names and IDs for each user in each company.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, name, and company fields
          new Document("$project", new Document("_id", 1).append("owner", 1)),
          // Group the users by company, and count the number of users in each company
          new Document("$group", new Document("_id", "$owner")
            // Count the number of users in each company
            .append("count", new Document("$sum", 1))
            // Collect the user names and IDs for each user in each company
            .append("users", new Document("$push", new Document("_id", "$_id").append("owner", "$owner")))),
          // Sort the results. Use the `sortby` query param (default "company")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to UserGroupResult objects
        // (i.e., a list of UserGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        TodoByCategory.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }


  public void getTodosGroupedByStatus(Context ctx)
  {
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "$oid");
    if (sortBy.equals("status")) {
      sortBy = "$oid";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    // The `UserByCompany` class is a simple class that has fields for the company
    // name, the number of users in that company, and a list of user names and IDs
    // (using the `UserIdName` class to store the user names and IDs).
    // We're going to use the aggregation pipeline to group users by company, and
    // then count the number of users in each company. We'll also collect the userserver/src/main/java/umm3601/user/UserIdName.java
    // names and IDs for each user in each company. We'll then convert the results
    // of the aggregation pipeline to `UserByCompany` objects.
    ArrayList<TodoByStatus> matchingTodos = todoCollection
      // The following aggregation pipeline groups users by company, and
      // then counts the number of users in each company. It also collects
      // the user names and IDs for each user in each company.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, name, and company fields
          new Document("$project", new Document("_id", 1).append("owner", 1).append("status", 1)),
          // Group the users by company, and count the number of users in each company
          new Document("$group", new Document("_id", "$status")
            // Count the number of users in each company
            .append("count", new Document("$sum", 1))
            // Collect the user names and IDs for each user in each company
            .append("todos", new Document("$push", new Document("_id", "$_id").append("owner", "$owner")))),
          // Sort the results. Use the `sortby` query param (default "company")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to UserGroupResult objects
        // (i.e., a list of UserGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        TodoByStatus.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }
  public void getTodosGroupedByCategory(Context ctx) {
    // We'll support sorting the results either by company name (in either `asc` or `desc` order)
    // or by the number of users in the company (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "$oid");
    if (sortBy.equals("category")) {
      sortBy = "$oid";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    // The `UserByCompany` class is a simple class that has fields for the company
    // name, the number of users in that company, and a list of user names and IDs
    // (using the `UserIdName` class to store the user names and IDs).
    // We're going to use the aggregation pipeline to group users by company, and
    // then count the number of users in each company. We'll also collect the userserver/src/main/java/umm3601/user/UserIdName.java
    // names and IDs for each user in each company. We'll then convert the results
    // of the aggregation pipeline to `UserByCompany` objects.

    ArrayList<TodoByCategory> matchingTodos = todoCollection
      // The following aggregation pipeline groups users by company, and
      // then counts the number of users in each company. It also collects
      // the user names and IDs for each user in each company.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, name, and company fields
          new Document("$project", new Document("_id", 1).append("owner", 1).append("category", 1)),
          // Group the users by company, and count the number of users in each company
          new Document("$group", new Document("_id", "$category")
            // Count the number of users in each company
            .append("count", new Document("$sum", 1))
            // Collect the user names and IDs for each user in each company
            .append("todos", new Document("$push", new Document("_id", "$_id").append("owner", "$owner")))),
          // Sort the results. Use the `sortby` query param (default "company")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to UserGroupResult objects
        // (i.e., a list of UserGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        TodoByCategory.class
      )
      .into(new ArrayList<>());

    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }

  public void filterLimit(Context ctx) {
    // We'll support sorting the results either by company name (in either `asc` or `desc` order)
    // or by the number of users in the company (`count`, also in either `asc` or `desc` order).
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("sortBy"), "$oid");
    if (sortBy.equals("owner")) {
      sortBy = "$status";
    }
    else if(sortBy.equals("status"))
    {
      sortBy = "$body";
    }
    else
    {
      sortBy = "$category";
    }
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortOrder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ?  Sorts.descending(sortBy) : Sorts.ascending(sortBy);

    // The `UserByCompany` class is a simple class that has fields for the company
    // name, the number of users in that company, and a list of user names and IDs
    // (using the `UserIdName` class to store the user names and IDs).
    // We're going to use the aggregation pipeline to group users by company, and
    // then count the number of users in each company. We'll also collect the userserver/src/main/java/umm3601/user/UserIdName.java
    // names and IDs for each user in each company. We'll then convert the results
    // of the aggregation pipeline to `UserByCompany` objects.
    System.out.println("Hello!");
    ArrayList<TodoByCategory> matchingTodos = todoCollection
      // The following aggregation pipeline groups users by company, and
      // then counts the number of users in each company. It also collects
      // the user names and IDs for each user in each company.
      .aggregate(
        List.of(
          // Project the fields we want to use in the next step, i.e., the _id, name, and company fields
          new Document("$project", new Document("_id", 1).append("owner", 1).append("status", 1).append("body", 1).append("category", 1)),
          // Group the users by company, and count the number of users in each company

          // Sort the results. Use the `sortby` query param (default "company")
          // as the field to sort by, and the query param `sortorder` (default
          // "asc") to specify the sort order.
          new Document("$sort", sortingOrder)
        ),
        // Convert the results of the aggregation pipeline to UserGroupResult objects
        // (i.e., a list of UserGroupResult objects). It is necessary to have a Java type
        // to convert the results to, and the JacksonMongoCollection will do this for us.
        TodoByCategory.class
      )
      .into(new ArrayList<>());

    while(matchingTodos.size() > getLimit(ctx))
    {
      matchingTodos.removeLast();
      System.out.println(matchingTodos.size());
    }
    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);
  }

  /**
   * Add a new user using information from the context
   * (as long as the information gives "legal" values to User fields)
   *
   * @param ctx a Javalin HTTP context that provides the user info
   *  in the JSON body of the request
   */
  public void addNewOwner(Context ctx) {
    /*
     * The follow chain of statements uses the Javalin validator system
     * to verify that instance of `User` provided in this context is
     * a "legal" user. It checks the following things (in order):
     *    - The user has a value for the name (`usr.name != null`)
     *    - The user name is not blank (`usr.name.length > 0`)
     *    - The provided email is valid (matches EMAIL_REGEX)
     *    - The provided age is > 0
     *    - The provided age is < REASONABLE_AGE_LIMIT
     *    - The provided role is valid (one of "admin", "editor", or "viewer")
     *    - A non-blank company is provided
     * If any of these checks fail, the Javalin system will throw a
     * `BadRequestResponse` with an appropriate error message.
     */
    String body = ctx.body();
    Todo newOwner = ctx.bodyValidator(Todo.class)
      .check(usr -> usr.owner != null && usr.owner.length() > 0,
        "Owner must have a non-empty name; body was " + body)
      .check(usr -> usr.status != true || usr.status != false,
        "Owner must possess a legal status; body was " + body)
      .check(usr -> usr.body.length() > 0,
        "The length of the owner's body must be greater than zero; body was " + body)
      .check(usr -> usr.category.length() > 0,
        "The length of the owner's category must be greater than zero; body was " + body)
      .get();

    // Add the new user to the database
    todoCollection.insertOne(newOwner);

    // Set the JSON response to be the `_id` of the newly created user.
    // This gives the client the opportunity to know the ID of the new user,
    // which it can then use to perform further operations (e.g., a GET request
    // to get and display the details of the new user).
    ctx.json(Map.of("id", newOwner._id));
    // 201 (`HttpStatus.CREATED`) is the HTTP code for when we successfully
    // create a new resource (a user in this case).
    // See, e.g., https://developer.mozilla.org/en-US/docs/Web/HTTP/Status
    // for a description of the various response codes.
    ctx.status(HttpStatus.CREATED);
  }

  /**
   * Delete the user specified by the `id` parameter in the request.
   *
   * @param ctx a Javalin HTTP context
   */
  public void deleteTodo(Context ctx) {
    String id = ctx.pathParam("id");
    DeleteResult deleteResult = todoCollection.deleteOne(eq("_id", new ObjectId(id)));
    // We should have deleted 1 or 0 users, depending on whether `id` is a valid user ID.
    if (deleteResult.getDeletedCount() != 1) {
      ctx.status(HttpStatus.NOT_FOUND);
      throw new NotFoundResponse(
        "Was unable to delete ID "
          + id
          + "; perhaps illegal ID or an ID for an item not in the system?");
    }
    ctx.status(HttpStatus.OK);
  }

  /**
   * Utility function to generate an URI that points
   * at a unique avatar image based on a user's email.
   *
   * This uses the service provided by gravatar.com; there
   * are numerous other similar services that one could
   * use if one wished.
   *
   * YOU DON'T NEED TO USE THIS FUNCTION FOR THE TODOS.
   *
   * @param email the email to generate an avatar for
   * @return a URI pointing to an avatar image
   */


  /**
   * Utility function to generate the md5 hash for a given string
   *
   * @param str the string to generate a md5 for
   */
  public String md5(String str) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    byte[] hashInBytes = md.digest(str.toLowerCase().getBytes(StandardCharsets.UTF_8));

    StringBuilder result = new StringBuilder();
    for (byte b : hashInBytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }


  /**
   * Setup routes for the `user` collection endpoints.
   *
   * These endpoints are:
   *   - `GET /api/users/:id`
   *       - Get the specified user
   *   - `GET /api/users?age=NUMBER&company=STRING&name=STRING`
   *      - List users, filtered using query parameters
   *      - `age`, `company`, and `name` are optional query parameters
   *   - `GET /api/usersByCompany`
   *     - Get user names and IDs, possibly filtered, grouped by company
   *   - `DELETE /api/users/:id`
   *      - Delete the specified user
   *   - `POST /api/users`
   *      - Create a new user
   *      - The user info is in the JSON body of the HTTP request
   *
   * GROUPS SHOULD CREATE THEIR OWN CONTROLLERS THAT IMPLEMENT THE
   * `Controller` INTERFACE FOR WHATEVER DATA THEY'RE WORKING WITH.
   * You'll then implement the `addRoutes` method for that controller,
   * which will set up the routes for that data. The `Server#setupRoutes`
   * method will then call `addRoutes` for each controller, which will
   * add the routes for that controller's data.
   *
   * @param server The Javalin server instance
   * @param userController The controller that handles the user endpoints
   */
  public void addRoutes(Javalin server) {
    // Get the specified user
    server.get(API_TODOS_BY_OID, this::getUser);

    // List users, filtered using query parameters
    server.get(API_TODOS, this::getUsers);

    // Get the users, possibly filtered, grouped by category
    server.get("/api/TodoByCategory", this::getTodosGroupedByCategory);

        //Get the users by owner (name)
    server.get("/api/TodoByOwner", this::getTodosGroupedByOwner);

    server.get("/api/TodoByStatus", this::getTodosGroupedByStatus);

    server.get("/api/TodoLimit", this::filterLimit);

    // Add new user with the user info being in the JSON body
    // of the HTTP request
    server.post(API_TODOS, this::addNewOwner);

    // Delete the specified user
    server.delete(API_TODOS_BY_OID, this::deleteTodo);
  }
}
