/*
 * Copyright 2016 Robert Winkler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.swagger2markup.internal.utils;

import io.github.swagger2markup.markup.builder.MarkupDocBuilder;
import io.github.swagger2markup.model.PathOperation;
import io.swagger.models.*;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExamplesUtil {

    private static Logger logger = LoggerFactory.getLogger(ExamplesUtil.class);

    /**
     * Generates a Map of response examples
     *
     * @param generateMissingExamples specifies the missing examples should be generated
     * @param operation the Swagger Operation
     * @param definitions the map of definitions
     * @param markupDocBuilder the markup builder
     * @return map containing response examples.
     */
    public static Map<String, Object> generateResponseExampleMap(boolean generateMissingExamples, Operation operation, Map<String, Model> definitions, MarkupDocBuilder markupDocBuilder) {
        Map<String, Object> examples = new LinkedHashMap<>();
        Map<String, Response> responses = operation.getResponses();
        if (responses != null)
            for (Map.Entry<String, Response> responseEntry : responses.entrySet()) {
                Response response = responseEntry.getValue();
                Object example = response.getExamples();
                if (example == null) {
                    Property schema = response.getSchema();
                    if (schema != null) {
                        example = schema.getExample();

                        if (example == null && schema instanceof RefProperty) {
                            String simpleRef = ((RefProperty) schema).getSimpleRef();
                            example = generateExampleForRefModel(generateMissingExamples, simpleRef, definitions, markupDocBuilder);
                        }
                        if (example == null && generateMissingExamples) {
                            example = PropertyUtils.generateExample(schema, markupDocBuilder);
                        }
                    }
                }

                if (example != null)
                    examples.put(responseEntry.getKey(), example);

            }

        return examples;
    }

    /**
     * Generates examples for request
     *
     * @param generateMissingExamples specifies the missing examples should be generated
     * @param pathOperation the Swagger Operation
     * @param definitions the map of definitions
     * @param markupDocBuilder the markup builder
     * @return an Optional with the example content
     */
    public static Map<String, Object> generateRequestExampleMap(boolean generateMissingExamples, PathOperation pathOperation, Map<String, Model> definitions, MarkupDocBuilder markupDocBuilder) {
        Operation operation = pathOperation.getOperation();
        List<Parameter> parameters = operation.getParameters();
        Map<String, Object> examples = new LinkedHashMap<>();

        // Path example should always be included (if generateMissingExamples):
        if (generateMissingExamples)
            examples.put("path", pathOperation.getPath());
        for (Parameter parameter : parameters) {
            Object example = null;
            if (parameter instanceof BodyParameter) {
                example = ((BodyParameter) parameter).getExamples();
                if (example == null) {
                    Model schema = ((BodyParameter) parameter).getSchema();
                    if (schema instanceof RefModel) {
                        String simpleRef = ((RefModel) schema).getSimpleRef();
                        example = generateExampleForRefModel(generateMissingExamples, simpleRef, definitions, markupDocBuilder);
                    } else if (generateMissingExamples) {
                        if (schema instanceof ComposedModel) {
                            example = exampleMapForProperties(getPropertiesForComposedModel(
                                    (ComposedModel) schema, definitions), definitions, markupDocBuilder);
                        } else if (schema instanceof ArrayModel) {
                            example = generateExampleForArrayModel((ArrayModel) schema, definitions, markupDocBuilder);
                        } else {
                            example = schema.getExample();
                            if (example == null) {
                                example = exampleMapForProperties(schema.getProperties(), definitions, markupDocBuilder);
                            }
                        }
                    }
                }
            } else if (parameter instanceof AbstractSerializableParameter) {
                if (generateMissingExamples) {
                    Object abstractSerializableParameterExample;
                    abstractSerializableParameterExample = ((AbstractSerializableParameter) parameter).getExample();
                    if (abstractSerializableParameterExample == null) {
                        Property item = ((AbstractSerializableParameter) parameter).getItems();
                        if (item != null) {
                            abstractSerializableParameterExample = item.getExample();
                            if (abstractSerializableParameterExample == null) {
                                abstractSerializableParameterExample = PropertyUtils.generateExample(item, markupDocBuilder);
                            }
                        }
                        if (abstractSerializableParameterExample == null) {
                            abstractSerializableParameterExample = ParameterUtils.generateExample((AbstractSerializableParameter)parameter);
                        }
                    }
                    if (parameter instanceof PathParameter) {
                        String pathExample = (String) examples.get("path");
                        pathExample = pathExample.replace('{' + parameter.getName() + '}', String.valueOf(abstractSerializableParameterExample));
                        example = pathExample;
                    } else {
                        example = abstractSerializableParameterExample;
                    }
                    if (parameter instanceof QueryParameter) {
                        //noinspection unchecked
                        @SuppressWarnings("unchecked")
                        Map<String, Object> queryExampleMap = (Map<String, Object>) examples.get("query");
                        if (queryExampleMap == null) {
                            queryExampleMap = new LinkedHashMap<>();
                        }
                        queryExampleMap.put(parameter.getName(), abstractSerializableParameterExample);
                        example = queryExampleMap;
                    }
                }
            } else if (parameter instanceof RefParameter) {
                String simpleRef = ((RefParameter) parameter).getSimpleRef();
                example = generateExampleForRefModel(generateMissingExamples, simpleRef, definitions, markupDocBuilder);
            }

            if (example != null)
                examples.put(parameter.getIn(), example);
        }

        return examples;
    }

    /**
     * Generates an example object from a simple reference
     *
     * @param generateMissingExamples specifies the missing examples should be generated
     * @param simpleRef the simple reference string
     * @param definitions the map of definitions
     * @param markupDocBuilder the markup builder
     * @return returns an Object or Map of examples
     */
    public static Object generateExampleForRefModel(boolean generateMissingExamples, String simpleRef, Map<String, Model> definitions, MarkupDocBuilder markupDocBuilder) {
        Model model = definitions.get(simpleRef);
        Object example = null;
        if (model != null) {
            example = model.getExample();
            if (example == null && generateMissingExamples) {
                if (model instanceof ComposedModel) {
                    example = exampleMapForProperties(getPropertiesForComposedModel((ComposedModel) model, definitions), definitions, markupDocBuilder);
                } else {
                    example = exampleMapForProperties(model.getProperties(), definitions, markupDocBuilder);
                }
            }
        }
        return example;
    }

    private static Map<String, Property> getPropertiesForComposedModel(ComposedModel model, Map<String, Model> definitions) {
        Map<String, Property> combinedProperties;
        if (model.getParent() instanceof RefModel) {
            Map<String, Property> parentProperties = definitions.get(((RefModel) model.getParent()).getSimpleRef()).getProperties();
            if (parentProperties == null) {
                return null;
            } else {
                combinedProperties = new LinkedHashMap<>(parentProperties);
            }

        } else {
            combinedProperties = new LinkedHashMap<>(model.getParent().getProperties());
        }
        Map<String, Property> childProperties;
        if (model.getChild() instanceof RefModel) {
            childProperties = definitions.get(((RefModel) model.getChild()).getSimpleRef()).getProperties();
        } else {
            childProperties = model.getChild().getProperties();
        }
        if (childProperties != null) {
            combinedProperties.putAll(childProperties);
        }
        return combinedProperties;
    }

    /**
     * Generates a map of examples from a map of properties. If defined examples are found, those are used. Otherwise,
     * examples are generated from the type.
     *
     * @param properties the map of properties
     * @param definitions the map of definitions
     * @param markupDocBuilder the markup builder
     *
     * @return a Map of examples
     */
    public static Map<String, Object> exampleMapForProperties(Map<String, Property> properties, Map<String, Model> definitions, MarkupDocBuilder markupDocBuilder) {
        Map<String, Object> exampleMap = new LinkedHashMap<>();
        for (Map.Entry<String, Property> property : properties.entrySet()) {
            Object exampleObject = property.getValue().getExample();
            if (exampleObject == null) {
                if (property.getValue() instanceof RefProperty) {
                    exampleObject = generateExampleForRefModel(true, ((RefProperty) property.getValue()).getSimpleRef(), definitions, markupDocBuilder);
                } else if (property.getValue() instanceof ArrayProperty) {
                    exampleObject = generateExampleForArrayProperty((ArrayProperty) property.getValue(), definitions, markupDocBuilder);
                } else if (property.getValue() instanceof MapProperty) {
                    exampleObject = generateExampleForMapProperty((MapProperty) property.getValue(), markupDocBuilder);
                }
                if (exampleObject == null) {
                    Property valueProperty = property.getValue();
                    exampleObject = PropertyUtils.generateExample(valueProperty, markupDocBuilder);
                }
            }
            exampleMap.put(property.getKey(), exampleObject);
        }
        return exampleMap;
    }

    public static Object generateExampleForMapProperty(MapProperty property, MarkupDocBuilder markupDocBuilder) {
        if (property.getExample() != null) {
            return property.getExample();
        }
        Map<String, Object> exampleMap = new LinkedHashMap<>();
        Property valueProperty = property.getAdditionalProperties();
        if (valueProperty.getExample() != null) {
            return valueProperty.getExample();
        }
        exampleMap.put("string", PropertyUtils.generateExample(valueProperty, markupDocBuilder));
        return exampleMap;
    }

    public static Object generateExampleForArrayModel(ArrayModel model, Map<String, Model> definitions, MarkupDocBuilder markupDocBuilder) {
        if (model.getExample() != null) {
            return model.getExample();
        } else if (model.getProperties() != null) {
            return new Object[]{exampleMapForProperties(model.getProperties(), definitions, markupDocBuilder)};
        } else {
            Property itemProperty = model.getItems();
            if (itemProperty.getExample() != null) {
                return new Object[]{itemProperty.getExample()};
            } else if (itemProperty instanceof ArrayProperty) {
                return new Object[]{generateExampleForArrayProperty((ArrayProperty) itemProperty, definitions, markupDocBuilder)};
            } else if (itemProperty instanceof RefProperty) {
                return new Object[]{generateExampleForRefModel(true, ((RefProperty) itemProperty).getSimpleRef(), definitions, markupDocBuilder)};
            } else {
                return new Object[]{PropertyUtils.generateExample(itemProperty, markupDocBuilder)};
            }
        }
    }

    /**
     * Generates examples from an ArrayProperty
     *
     * @param value ArrayProperty
     * @param definitions map of definitions
     * @param markupDocBuilder the markup builder
     * @return array of Object
     */
    public static Object[] generateExampleForArrayProperty(ArrayProperty value, Map<String, Model> definitions, MarkupDocBuilder markupDocBuilder) {
        Property property = value.getItems();
        if (property.getExample() != null) {
            return new Object[]{property.getExample()};
        } else if (property instanceof ArrayProperty) {
            return new Object[]{generateExampleForArrayProperty((ArrayProperty) property, definitions, markupDocBuilder)};
        } else if (property instanceof RefProperty) {
            return new Object[]{generateExampleForRefModel(true, ((RefProperty) property).getSimpleRef(), definitions, markupDocBuilder)};
        } else {
            return new Object[]{PropertyUtils.generateExample(property, markupDocBuilder)};
        }
    }

}
