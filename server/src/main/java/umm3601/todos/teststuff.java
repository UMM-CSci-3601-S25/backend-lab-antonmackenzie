/*
 *
 *
 * if (ctx.queryParamMap().containsKey(LIMIT_KEY)) {
      int todoLimit = ctx.queryParamAsClass(LIMIT_KEY, Integer.class)
        .check(it -> it > 0, "Todo's Limit must be greater than zero; you provided " + ctx.queryParam(LIMIT_KEY))
        .get();
        filters.add(eq(LIMIT_KEY, todoLimit));
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
    }
    ctx.json(matchingTodos);
    ctx.status(HttpStatus.OK);

    server.get("/api/todoLimit", this::filterLimit);


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

  }
 */
