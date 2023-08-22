package fi.csc.chipster.comp;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fi.csc.chipster.comp.ToolDescription.ParameterDescription;
import fi.csc.chipster.toolbox.sadl.SADLSyntax.ParameterType;

public class JobMessageUtils {
	/**
	 * This should really be in the GenericJobMessage, but static methods in
	 * interfaces are only allowed starting from Java 1.8.
	 * 
	 * @param securityPolicy
	 * @param description
	 * @param parameters
	 * @return
	 * @throws ParameterValidityException
	 */
	public static List<String> checkParameterSafety(ParameterSecurityPolicy securityPolicy, ToolDescription description,
			List<String> parameters) throws ParameterValidityException {
		// Do argument checking first
		if (securityPolicy == null) {
			throw new IllegalArgumentException("security policy cannot be null");
		}
		if (description == null) {
			throw new IllegalArgumentException("tool description cannot be null");
		}

		// Count parameter descriptions
		int parameterDescriptionCount = 0;
		for (Iterator<ParameterDescription> iterator = description.getParameters().iterator(); iterator
				.hasNext(); iterator.next()) {
			parameterDescriptionCount++;
		}

		// Check that description and values match
		if (parameterDescriptionCount != parameters.size()) {
			throw new IllegalArgumentException(
					"number of parameter descriptions does not match the number of parameter values");
		}

		// Check if there are any disallowed characters in the parameter value 
		Iterator<ParameterDescription> descriptionIterator = description.getParameters().iterator();
		for (String parameter : parameters) {
			ParameterDescription parameterDescription = descriptionIterator.next();

			if (parameterDescription.isChecked()) {
				if (!securityPolicy.isValueValid(parameter, parameterDescription)) {
					throw new ParameterValidityException(
							"illegal value for parameter " + parameterDescription.getName() + ": " + parameter);
				}
			} else {
				if (!securityPolicy.allowUncheckedParameters(description)) {
					throw new UnsupportedOperationException("unchecked parameters are not allowed");
				}
			}
		}
		
//	    // Check that the selected enum option exists
//		// Should we check also other parameter constraints like integer limits?
//        Iterator<ParameterDescription> descriptionIterator2 = description.getParameters().iterator();
//        for (String parameter : parameters) {
//            ParameterDescription parameterDescription = descriptionIterator2.next();
//
//            if (parameterDescription.getType() == ParameterType.ENUM) {
//                Set<String> options = Stream.of(parameterDescription.getSelectionOptions()).map(o -> o.getID()).collect(Collectors.toSet());
//                
//                if (!options.contains(parameter)) {
//                    throw new ParameterValidityException(
//                            "enum parameter " + parameterDescription.getName() + " does not have option " + parameter);
//                }
//            }
//        }
				
		// Everything was ok, return the parameters
		return parameters;
	}
}
