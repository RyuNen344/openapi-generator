package org.openapitools.client;

import org.openapitools.client.model.*;
import java.lang.Exception;
import com.fasterxml.jackson.databind.JsonMappingException;

import org.junit.*;
import static org.junit.Assert.*;


public class JSONComposedSchemaTest {
    JSON json = null;

    @Before
    public void setup() {
        json = new JSON();
    }

    /**
     * Validate a oneOf schema can be deserialized into the expected class.
     * The oneOf schema does not have a discriminator. 
     */
    @Test
    public void testOneOfSchemaWithoutDiscriminator() throws Exception {
        // BananaReq and AppleReq have explicitly defined properties that are different by name.
        // There is no discriminator property.
        {
            String str = "{ \"cultivar\": \"golden delicious\", \"mealy\": false }";
            FruitReq o = json.getContext(null).readValue(str, FruitReq.class);
            assertTrue(o.getActualInstance() instanceof AppleReq);
            AppleReq inst = (AppleReq) o.getActualInstance();
            assertEquals(inst.getCultivar(), "golden delicious");
            assertEquals(inst.getMealy(), false);
        }
        {
            // Same test, but this time with additional (undeclared) properties.
            // Since FruitReq has additionalProperties: false, deserialization should fail.
            String str = "{ \"cultivar\": \"golden delicious\", \"mealy\": false, \"garbage_prop\": \"abc\" }";
            Exception exception = assertThrows(JsonMappingException.class, () -> {
                FruitReq o = json.getContext(null).readValue(str, FruitReq.class);
            });
            assertTrue(exception.getMessage().contains("Failed deserialization for FruitReq: 0 classes match result"));
        }
        {
            String str = "{ \"lengthCm\": 17 }";
            FruitReq o = json.getContext(null).readValue(str, FruitReq.class);
            assertTrue(o.getActualInstance() instanceof BananaReq);
            BananaReq inst = (BananaReq) o.getActualInstance();
            assertEquals(inst.getLengthCm(), new java.math.BigDecimal(17));
        }
        {
            // Try to deserialize empty object. This should fail 'oneOf' because that will match
            // both AppleReq and BananaReq.
            String str = "{ }";
            Exception exception = assertThrows(JsonMappingException.class, () -> {
                json.getContext(null).readValue(str, FruitReq.class);
            });
            assertTrue(exception.getMessage().contains("Failed deserialization for FruitReq: 2 classes match result"));
            // TODO: add a similar unit test where the oneOf child schemas have required properties.
            // If the implementation is correct, the unmarshaling should take the "required" keyword
            // into consideration, which it is not doing currently.
        }
        {
            // Deserialize the null value. This should be allowed because the 'FruitReq' schema
            // has nullable: true.
            String str = "null";
            FruitReq o = json.getContext(null).readValue(str, FruitReq.class);
            assertNull(o);
        }        
    }

    /**
     * Validate a oneOf schema can be deserialized into the expected class.
     * The oneOf schema has a discriminator. 
     */
    @Test
    public void testOneOfSchemaWithDiscriminator() throws Exception {
        // Mammal can be one of whale, pig and zebra.
        // pig has sub-classes.
        {
            String str = "{ \"className\": \"whale\", \"hasBaleen\": true, \"hasTeeth\": false }";
            
            // Note that the 'zebra' schema does not have any explicit property defined AND
            // it has additionalProperties: true. Hence without a discriminator the above
            // JSON payload would match both 'whale' and 'zebra'. This is because the 'hasBaleen'
            // and 'hasTeeth' would be considered additional (undeclared) properties for 'zebra'.
            AbstractOpenApiSchema o = json.getContext(null).readValue(str, Mammal.class);
            assertNotNull(o);
            assertTrue(o.getActualInstance() instanceof Whale);
        }
        {
            String str = "{ \"className\": \"zebra\", \"type\": \"plains\" }";
            AbstractOpenApiSchema o = json.getContext(null).readValue(str, Mammal.class);
            assertNotNull(o);
            assertTrue(o.getActualInstance() instanceof Zebra);
            Zebra z = (Zebra)o.getActualInstance();
            assertEquals(Zebra.TypeEnum.PLAINS, z.getType());
        }
        {
            // The discriminator value is valid but the 'type' value is invalid.
            String str = "{ \"className\": \"zebra\", \"type\": \"garbage_value\" }";
            Exception exception = assertThrows(JsonMappingException.class, () -> {
                json.getContext(null).readValue(str, Mammal.class);
            });
        }
        {
            // The discriminator value is zebra but the properties belong to Whale.
            // The 'whale' properties are considered to be additional (undeclared) properties
            // because in the 'zebra' schema, the 'additionalProperties' keyword has been set
            // to true.
            // TODO: The outcome should depend on the value of the 'useOneOfDiscriminatorLookup' CLI.
            String str = "{ \"className\": \"zebra\", \"hasBaleen\": true, \"hasTeeth\": false }";
            AbstractOpenApiSchema o = json.getContext(null).readValue(str, Mammal.class);
            assertNotNull(o);
            assertTrue(o.getActualInstance() instanceof Zebra);
        }
        {
            String str = "{ \"className\": \"zebra\" }";
            AbstractOpenApiSchema o = json.getContext(null).readValue(str, Mammal.class);
            assertNotNull(o);
            assertTrue(o.getActualInstance() instanceof Zebra);
        }
        {
            /* comment out while unboxing nested oneOf/anyOf is still in discussion
            // Deserialization test with indirections of 'oneOf' child schemas.
            // Mammal is oneOf whale, zebra and pig, and pig is itself one of BasquePig, DanishPig.
            String str = "{ \"className\": \"BasquePig\" }";
            AbstractOpenApiSchema o = json.getContext(null).readValue(str, Mammal.class);
            assertTrue(o.getActualInstance() instanceof BasquePig);
            */
        }
    }

    @Test
    public void testOneOfNullable() throws Exception {
        String str = "null";
        // 'null' is a valid value for NullableShape because it is nullable.
        AbstractOpenApiSchema o = json.getContext(null).readValue(str, NullableShape.class);
        assertNull(o);

        // 'null' is a valid value for ShapeOrNull because it is a oneOf with one of the
        // children being the null type.
        o = json.getContext(null).readValue(str, ShapeOrNull.class);
        assertNull(o);

        // 'null' is not a valid value for the Shape model because it is not nullable.
        // An exception should be raised.
        Exception exception = assertThrows(JsonMappingException.class, () -> {
            json.getContext(null).readValue(str, Shape.class);
        });
        assertEquals("Shape cannot be null", exception.getMessage());
    }

    /**
     * Test payload with more than one discriminator.
     */
    @Test
    public void testOneOfMultipleDiscriminators() throws Exception {
        // 'shapeType' is a discriminator for the 'Shape' model and
        // 'triangleType' is a discriminator forr the 'Triangle' model.
        String str = "{ \"shapeType\": \"Triangle\", \"triangleType\": \"EquilateralTriangle\" }";

        // We should be able to deserialize a equilateral triangle into a EquilateralTriangle class.
        EquilateralTriangle t = json.getContext(null).readValue(str, EquilateralTriangle.class);
        assertNotNull(t);

        // We should be able to deserialize a equilateral triangle into a triangle.
        AbstractOpenApiSchema o = json.getContext(null).readValue(str, Triangle.class);
        assertNotNull(o);
        assertTrue(o.getActualInstance() instanceof EquilateralTriangle);

        // We should be able to deserialize a equilateral triangle into a shape.
        o = json.getContext(null).readValue(str, Shape.class);
        // The container is a shape, and the actual instance should be a EquilateralTriangle.        
        assertTrue(o instanceof Shape);
        /* comment out while unboxing nested oneOf/anyOf is still in discussion
        assertTrue(o.getActualInstance() instanceof EquilateralTriangle);

        // It is not valid to deserialize a equilateral triangle into a quadrilateral.
        Exception exception = assertThrows(JsonMappingException.class, () -> {
            json.getContext(null).readValue(str, Quadrilateral.class);
        });
        assertTrue(exception.getMessage().contains("Failed deserialization for Quadrilateral: 0 classes match result"));
        */
    }

    @Test
    public void testOneOfNestedComposedSchema() throws Exception {
        /*
        {
            String str = "{ " +
                " \"mainShape\":      { \"shapeType\": \"Triangle\", \"triangleType\": \"EquilateralTriangle\" }, " +
                " \"shapeOrNull\":    { \"shapeType\": \"Triangle\", \"triangleType\": \"IsoscelesTriangle\" }, " +
                " \"nullableShape\":  { \"shapeType\": \"Triangle\", \"triangleType\": \"ScaleneTriangle\" } " +
            "}";
            Drawing d = json.getContext(null).readValue(str, Drawing.class);
            assertNotNull(d);
            assertNotNull(d.getMainShape());
            assertNotNull(d.getShapeOrNull());
            assertNotNull(d.getNullableShape());
            assertTrue(d.getMainShape().getActualInstance() instanceof EquilateralTriangle);
            assertTrue(d.getShapeOrNull().getActualInstance() instanceof IsoscelesTriangle);
            assertTrue(d.getNullableShape().getActualInstance() instanceof ScaleneTriangle);
        }

        {
            String str = "{ " +
                " \"mainShape\":      { \"shapeType\": \"Triangle\", \"triangleType\": \"EquilateralTriangle\" }, " +
                " \"shapeOrNull\":    null, " +
                " \"nullableShape\":  null " +
            "}";
            Drawing d = json.getContext(null).readValue(str, Drawing.class);
            assertNotNull(d);
            assertNotNull(d.getMainShape());
            assertNull(d.getShapeOrNull());
            assertNull(d.getNullableShape());
            assertTrue(d.getMainShape().getActualInstance() instanceof EquilateralTriangle);
        }
        */
    }

    /**
     * Validate a allOf schema can be deserialized into the expected class.
     */
    @Test
    public void testAllOfSchema() throws Exception {
        {
            String str = "{ \"className\": \"Dog\", \"color\": \"white\",  \"breed\": \"Siberian Husky\" }";

            // We should be able to deserialize a dog into a Dog.
            Dog d = json.getContext(null).readValue(str, Dog.class);            
            assertNotNull(d);
            assertEquals("white", d.getColor());
        }
        {
            String str = "{ \"pet_type\": \"ChildCat\", \"name\": \"fluffy\" }";
            GrandparentAnimal o = json.getContext(null).readValue(str, GrandparentAnimal.class);            
            assertNotNull(o);
            assertTrue(o instanceof ParentPet);
            assertTrue(o instanceof ChildCat);
            ChildCat c = (ChildCat)o;
            assertEquals("fluffy", c.getName());
        }
        {
            String str = "{ \"pet_type\": \"ChildCat\", \"name\": \"fluffy\" }";
            ParentPet o = json.getContext(null).readValue(str, ParentPet.class);            
            assertNotNull(o);
            assertTrue(o instanceof ChildCat);
            ChildCat c = (ChildCat)o;
            assertEquals("fluffy", c.getName());
        }
        {
            // Wrong discriminator value in the payload.
            String str = "{ \"pet_type\": \"Garbage\", \"name\": \"fluffy\" }";
            Exception exception = assertThrows(JsonMappingException.class, () -> {
                json.getContext(null).readValue(str, GrandparentAnimal.class);
            });
            assertTrue(exception.getMessage().contains("Could not resolve type id 'Garbage'"));
        }
    }

    @Test
    public void testNullValueDisallowed() throws Exception {
        {
            String str = "{ \"id\": 123, \"petId\": 345, \"quantity\": 100, \"status\": \"placed\" }";
            Order o = json.getContext(null).readValue(str, Order.class);
            assertEquals(100L, (long)o.getQuantity());
            assertEquals(Order.StatusEnum.PLACED, o.getStatus());
        }
        {
            String str = "{ \"id\": 123, \"petId\": 345, \"quantity\": null }";
            Order o = json.getContext(null).readValue(str, Order.class);
            // TODO: the null value is not allowed per OAS document.
            // The deserialization should fail.
            assertNull(o.getQuantity());
        }
    }

    /**
     * Validate a anyOf schema can be deserialized into the expected class.
     * The anyOf schema has a discriminator. 
     */
    @Test
    public void testAnyOfSchemaWithoutDiscriminator() throws Exception {
        {
            // TODO: the GmFruit defines a 'color' property, which should be allowed
            // in the input data, but the generated code does not have it.
            String str = "{ \"cultivar\": \"golden delicious\", \"origin\": \"California\" }";
            GmFruit o = json.getContext(null).readValue(str, GmFruit.class);
            assertTrue(o.getActualInstance() instanceof Apple);
            Apple inst = (Apple) o.getActualInstance();
            assertEquals("golden delicious", inst.getCultivar());
            assertEquals("California", inst.getOrigin());
            // TODO: the 'Color' property is not generated for the 'GmFruit'.
            //assertEquals("yellow", o.getColor());
        }
        {
            String str = "{ \"lengthCm\": 17 }";
            GmFruit o = json.getContext(null).readValue(str, GmFruit.class);
            assertTrue(o.getActualInstance() instanceof Banana);
            Banana inst = (Banana) o.getActualInstance();
            assertEquals(new java.math.BigDecimal(17), inst.getLengthCm());
        }
        {
            // Deserialize empty object. This should work because it will match either apple or banana.
            String str = "{ }";
            GmFruit o = json.getContext(null).readValue(str, GmFruit.class);
            // The payload matches against either apple or banana, so either model could be returned,
            // but the implementation always picks the first anyOf child schema that matches the
            // input payload.
            assertTrue(o.getActualInstance() instanceof Apple);
        }
        {
            // Deserialize the null value. This is not allowed because the 'gmFruit' schema
            // is not nullable.
            String str = "null";
            Exception exception = assertThrows(JsonMappingException.class, () -> {
                GmFruit o = json.getContext(null).readValue(str, GmFruit.class);
            });
            assertEquals("GmFruit cannot be null", exception.getMessage());
        }  
    }
}
