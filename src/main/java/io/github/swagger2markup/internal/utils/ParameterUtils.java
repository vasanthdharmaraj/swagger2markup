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

import com.google.common.base.Function;
import io.github.swagger2markup.internal.type.*;
import io.swagger.models.Model;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.RefParameter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Map;


public final class ParameterUtils {

    /**
     * Retrieves the type of a parameter, or otherwise null
     *
     * @param parameter the parameter
     * @param definitionDocumentResolver the defintion document resolver
     * @return the type of the parameter, or otherwise null
     */
    public static Type getType(Parameter parameter, Map<String, Model> definitions, Function<String, String> definitionDocumentResolver){
        Validate.notNull(parameter, "parameter must not be null!");
        Type type = null;
        
        if(parameter instanceof BodyParameter){
            BodyParameter bodyParameter = (BodyParameter)parameter;
            Model model = bodyParameter.getSchema();
            
            if(model != null){
                type = ModelUtils.getType(model, definitions, definitionDocumentResolver);
            }else{
                type = new BasicType("string", null);
            }

        }
        else if(parameter instanceof AbstractSerializableParameter){
            AbstractSerializableParameter serializableParameter = (AbstractSerializableParameter)parameter;
            @SuppressWarnings("unchecked")
            List<String> enums = serializableParameter.getEnum();
            
            if(CollectionUtils.isNotEmpty(enums)){
                type = new EnumType(null, enums);
            }else{
                type = new BasicType(serializableParameter.getType(), null, serializableParameter.getFormat());
            }
            if(serializableParameter.getType().equals("array")){
                String collectionFormat = serializableParameter.getCollectionFormat();
                
                type = new ArrayType(null, PropertyUtils.getType(serializableParameter.getItems(), definitionDocumentResolver), collectionFormat);
            }
        }
        else if(parameter instanceof RefParameter){
            String refName = ((RefParameter)parameter).getSimpleRef();
            
            type = new RefType(definitionDocumentResolver.apply(refName), new ObjectType(refName, null /* FIXME, not used for now */));
        }
        return type;
    }

    /**
     * Retrieves the default value of a parameter, or otherwise returns null
     *
     * @param parameter the parameter
     * @return the default value of the parameter, or otherwise null
     */
    public static Object getDefaultValue(Parameter parameter){
        Validate.notNull(parameter, "parameter must not be null!");
        Object defaultValue = null;
        
        if(parameter instanceof AbstractSerializableParameter){
            AbstractSerializableParameter serializableParameter = (AbstractSerializableParameter)parameter;
            defaultValue = serializableParameter.getDefaultValue();
        }
        return defaultValue;
    }

    /**
     * Generate a default example value for parameter.
     *
     * @param parameter parameter
     * @return a generated example for the parameter
     */
    public static Object generateExample(AbstractSerializableParameter parameter) {
        switch (parameter.getType()) {
            case "integer":
                return 0;
            case "number":
                return 0.0;
            case "boolean":
                return true;
            case "string":
                return "string";
            default:
                return parameter.getType();
        }
    }
}
