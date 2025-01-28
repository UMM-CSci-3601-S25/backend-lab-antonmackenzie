package umm3601.todos;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class Todo {

  @ObjectId @Id
  // By default Java field names shouldn't start with underscores.
  // Here, though, we *have* to use the name `_id` to match the
  // name of the field as used by MongoDB.
  @SuppressWarnings({"MemberName"})
  public String _id;
  public String owner;
  public boolean status;
  public String body;
  public String category;


  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Todo)) {
      return false;
    }
    Todo other = (Todo) obj;
    return _id.equals(other._id);
  }

  @Override
  public int hashCode() {
    // This means that equal Users will hash the same, which is good.
    return _id.hashCode();
  }

  // Having some kind of `toString()` allows us to print `User`s,
  // which can be useful/necessary in error handling. This only
  // returns the name, but it could be extended to return more or
  // all of the fields combined into a single string.
  //
  // The other option would be to return `_id`, but that can be
  // `null` if we're trying to add a new `User` to the database
  // that doesn't yet have an `_id`, so returning `name` seemed
  // the better bet.
  @Override
  public String toString() {
    return owner;
  }
}
