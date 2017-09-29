package s4lab.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import s4lab.TimeUtils;

import java.sql.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class QueryBuilder {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final Connection connection;
  private PreparedStatement statement;
  private PreparedStatementWrapper statementWrapper;

  QueryBuilder(Connection connection) {
    this.connection = connection;
  }

  private void close() {
    try {
      if (!statement.isClosed()) {
        statement.close();
      }
    } catch (SQLException e) {
      logger.error("Could not close statement", e);
    }

    try {
      if (!connection.isClosed()) {
        connection.close();
      }
    } catch (SQLException e) {
      logger.error("Could not close connection", e);
    }
  }

  public QueryBuilder withStatement(String sql) {
    try {
      statement = connection.prepareStatement(sql);
      statementWrapper = new PreparedStatementWrapper(statement);
      return this;
    } catch (Throwable t) {
      close();
      throw new DatabaseException(t);
    }
  }

  public PreparedStatementWrapper withParam() {
    return statementWrapper;
  }

  public <T> List<T> executeQuery(ResultSetHandler<T> handler) {
    try (ResultSet resultSet = statement.executeQuery()) {
      ResultSetWrapper resultSetWrapper = new ResultSetWrapper(resultSet);
      List<T> results = new ArrayList<>();
      while (resultSet.next()) {
        results.add(handler.handle(resultSetWrapper));
      }
      return results;
    } catch (Throwable t) {
      close();
      throw new DatabaseException(t);
    }
  }

  public <T> T executeQueryForObject(ResultSetHandler<T> handler) {
    try (ResultSet resultSet = statement.executeQuery()) {
      T result = null;
      if (resultSet.next()) {
        ResultSetWrapper resultSetWrapper = new ResultSetWrapper(resultSet);
        result = handler.handle(resultSetWrapper);
        if (resultSet.next()) {
          throw new IndexOutOfBoundsException("Too many results");
        }
      }
      return result;
    } catch (Throwable t) {
      throw new DatabaseException(t);
    } finally {
      close();
    }
  }

  public int executeUpdate() {
    try {
      return statement.executeUpdate();
    } catch (Throwable t) {
      throw new DatabaseException(t);
    } finally {
      close();
    }
  }

  public <T> int executeUpdate(List<T> values, ValueMapper<T> valueMapper) {
    int results = 0;
    try {
      for (T value : values) {
        valueMapper.mapValue(value, statementWrapper);
        results += statement.executeUpdate();
      }
      return results;
    } catch (Throwable t) {
      throw new DatabaseException(t);
    } finally {
      close();
    }
  }

  public interface ResultSetHandler<T> {
    T handle(ResultSetWrapper resultSet) throws SQLException;
  }

  public interface ValueMapper<T> {
    void mapValue(T value, PreparedStatementWrapper statement);
  }

  public class PreparedStatementWrapper {
    private final PreparedStatement preparedStatement;

    PreparedStatementWrapper(PreparedStatement preparedStatement) {
      this.preparedStatement = preparedStatement;
    }

    public QueryBuilder stringValue(int index, String value) {
      try {
        preparedStatement.setString(index, value);
        return QueryBuilder.this;
      } catch (Throwable t) {
        throw new DatabaseException(t);
      }
    }

    public QueryBuilder timestampValue(int index, ZonedDateTime value) {
      Timestamp ts = TimeUtils.at(value).toTimestamp(ZoneOffset.UTC);
      try {
        preparedStatement.setTimestamp(index, ts);
        return QueryBuilder.this;
      } catch (Throwable t) {
        throw new DatabaseException(t);
      }
    }

    public QueryBuilder intValue(int index, int value) {
      try {
        preparedStatement.setInt(index, value);
        return QueryBuilder.this;
      } catch (Throwable t) {
        throw new DatabaseException(t);
      }
    }

    public QueryBuilder booleanValue(int index, boolean value) {
      try {
        preparedStatement.setBoolean(index, value);
        return QueryBuilder.this;
      } catch (Throwable t) {
        throw new DatabaseException(t);
      }
    }
  }
}
