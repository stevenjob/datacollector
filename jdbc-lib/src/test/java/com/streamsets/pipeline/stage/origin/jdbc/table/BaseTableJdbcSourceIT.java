/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.jdbc.table;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class BaseTableJdbcSourceIT {
  protected static final String USER_NAME = "sa";
  protected static final String PASSWORD = "sa";
  protected static final String database = "TEST";
  protected static final String JDBC_URL = "jdbc:h2:mem:" + database;

  private static final Logger LOG = LoggerFactory.getLogger(BaseTableJdbcSourceIT.class);


  protected static final String CREATE_STATEMENT_TEMPLATE = "CREATE TABLE IF NOT EXISTS %s.%s ( %s )";
  protected static final String INSERT_STATEMENT_TEMPLATE = "INSERT INTO %s.%s values ( %s )";
  protected static final String DROP_STATEMENT_TEMPLATE = "DROP TABLE IF EXISTS %s.%s";

  protected static final Joiner COMMA_SPACE_JOINER = Joiner.on(", ");

  protected static Connection connection;

  @BeforeClass
  public static void setup() throws SQLException {
    connection = DriverManager.getConnection(JDBC_URL, USER_NAME, PASSWORD);
  }

  @AfterClass
  public static void tearDown() throws SQLException {
    connection.close();
  }

  protected static String getStringRepOfFieldValueForInsert(Field field) {
    switch (field.getType()) {
      case BYTE_ARRAY:
        //Do a hex encode.
        return Hex.encodeHexString(field.getValueAsByteArray());
      case BYTE:
        return String.valueOf(field.getValueAsInteger());
      case TIME:
        return DateFormatUtils.format(field.getValueAsDate(), "HH:mm:ss.SSS");
      case DATE:
        return DateFormatUtils.format(field.getValueAsDate(), "yyyy-MM-dd");
      case DATETIME:
        return DateFormatUtils.format(field.getValueAsDate(), "yyyy-MM-dd HH:mm:ss.SSS");
      default:
        return String.valueOf(field.getValue());
    }
  }


  protected static String getCreateStatement(
      String schemaName,
      String tableName,
      Map<String, String> offsetFields,
      Map<String, String> otherFields
  ) {
    List<String> fieldFormats = new ArrayList<>();
    for (Map.Entry<String, String> offsetFieldEntry : offsetFields.entrySet()) {
      fieldFormats.add(offsetFieldEntry.getKey() + " " + offsetFieldEntry.getValue());
    }

    for (Map.Entry<String, String> otherFieldEntry : otherFields.entrySet()) {
      fieldFormats.add(otherFieldEntry.getKey() + " " + otherFieldEntry.getValue());
    }

    if (!offsetFields.isEmpty()) {
      fieldFormats.add("PRIMARY KEY(" + COMMA_SPACE_JOINER.join(offsetFields.keySet()) + ")");
    }

    String createQuery = String.format(CREATE_STATEMENT_TEMPLATE, schemaName, tableName, COMMA_SPACE_JOINER.join(fieldFormats));
    LOG.info("Created Query : " + createQuery);

    return createQuery;
  }

  protected static String getInsertStatement(String schemaName, String tableName, Collection<Field> fields) {
    List<String> fieldFormats = new ArrayList<>();
    for (Field field : fields) {
      String fieldFormat =
          (
              field.getType().isOneOf(
                  Field.Type.DATE,
                  Field.Type.TIME,
                  Field.Type.DATETIME,
                  Field.Type.CHAR,
                  Field.Type.STRING
              )
          )? "'"+ getStringRepOfFieldValueForInsert(field) +"'" : getStringRepOfFieldValueForInsert(field);

      fieldFormats.add(fieldFormat);
    }
    String insertQuery = String.format(INSERT_STATEMENT_TEMPLATE, schemaName, tableName, COMMA_SPACE_JOINER.join(fieldFormats));
    LOG.info("Created Query : " + insertQuery);
    return insertQuery;
  }


  protected static void insertRows(String insertTemplate, List<Record> records) throws SQLException {
    try (Statement st = connection.createStatement()) {
      for (Record record : records) {
        List<String> values = new ArrayList<>();
        for (String fieldPath : record.getEscapedFieldPaths()) {
          //Skip root field
          if (!fieldPath.equals("")) {
            values.add(getStringRepOfFieldValueForInsert(record.get(fieldPath)));
          }
        }
        st.addBatch(String.format(insertTemplate, values.toArray()));
      }
      st.executeBatch();
    }
  }


  private static void checkField(String fieldPath, Field expectedField, Field actualField) throws Exception {
    String errorString = String.format("Error in Field Path: %s", fieldPath);
    Assert.assertEquals(errorString, expectedField.getType(), actualField.getType());
    errorString = errorString + " of type: " + expectedField.getType().name();
    switch (expectedField.getType()) {
      case MAP:
      case LIST_MAP:
        Map<String, Field> expectedFieldMap = expectedField.getValueAsMap();
        Map<String, Field> actualFieldMap = actualField.getValueAsMap();
        Assert.assertEquals(errorString, expectedFieldMap.keySet().size(), actualFieldMap.keySet().size());
        for (Map.Entry<String, Field> entry : actualFieldMap.entrySet()) {
          String actualFieldName = entry.getKey().toLowerCase();
          Assert.assertNotNull(errorString, expectedFieldMap.get(actualFieldName));
          checkField(fieldPath + "/" + actualFieldName, expectedFieldMap.get(actualFieldName), entry.getValue());
        }
        break;
      case LIST:
        List<Field> expectedFieldList = expectedField.getValueAsList();
        List<Field> actualFieldList = actualField.getValueAsList();
        Assert.assertEquals(errorString, expectedFieldList.size(), actualFieldList.size());
        for (int i = 0; i < expectedFieldList.size(); i++) {
          checkField(fieldPath + "[" + i + "]", expectedFieldList.get(i), actualFieldList.get(i));
        }
        break;
      case BYTE_ARRAY:
        Assert.assertArrayEquals(errorString, expectedField.getValueAsByteArray(), actualField.getValueAsByteArray());
        break;
      default:
        Assert.assertEquals(errorString, expectedField.getValue(), actualField.getValue());
    }
  }

  static void checkRecords(List<Record> expectedRecords, List<Record> actualRecords) throws Exception {
    Assert.assertEquals("Record Size Does not match.", expectedRecords.size(), actualRecords.size());
    for (int i = 0; i < actualRecords.size(); i++) {
      Record actualRecord = actualRecords.get(i);
      Record expectedRecord = expectedRecords.get(i);
      checkField("", expectedRecord.get(), actualRecord.get());
    }
  }
}
