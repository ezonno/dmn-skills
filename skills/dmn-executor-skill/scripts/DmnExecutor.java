///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS org.kie:bom:10.1.0@pom
//DEPS org.kie:kie-dmn-api:10.1.0
//DEPS org.kie:kie-dmn-core:10.1.0
//DEPS org.slf4j:slf4j-simple:2.0.9
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0
//DEPS com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0

import org.kie.dmn.api.core.*;
import org.kie.dmn.api.core.ast.*;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.internal.io.ResourceFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class DmnExecutor {

    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String... args) throws Exception {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];

        switch (command) {
            case "execute" -> executeDecision(args);
            case "service" -> executeDecisionService(args);
            case "info" -> showModelInfo(args);
            case "help" -> printUsage();
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void executeDecision(String[] args) throws Exception {
        ParsedArgs parsed = parseArgs(args);
        
        if (parsed.mainDmnFile == null) {
            System.err.println("Error: DMN file path required");
            System.exit(1);
        }

        Map<String, Object> inputContext = mapper.readValue(
            parsed.inputJson, 
            new TypeReference<Map<String, Object>>() {}
        );

        DMNRuntime runtime = createRuntime(parsed.mainDmnFile, parsed.importPaths, parsed.autoImport, parsed.runtimeTypeCheck);
        DMNModel model = findMainModel(runtime, parsed.mainDmnFile, parsed.modelName);

        if (model == null) {
            outputError("Could not find main DMN model");
            System.exit(1);
        }

        if (model.hasErrors()) {
            outputErrors(model.getMessages());
            System.exit(1);
        }

        DMNContext context = runtime.newContext();
        inputContext.forEach(context::set);

        DMNResult result;
        if (parsed.serviceName != null && !parsed.serviceName.isEmpty()) {
            // Execute via Decision Service
            result = runtime.evaluateDecisionService(model, context, parsed.serviceName);
        } else if (parsed.decisionName != null && !parsed.decisionName.isEmpty()) {
            result = runtime.evaluateByName(model, context, parsed.decisionName);
        } else {
            result = runtime.evaluateAll(model, context);
        }

        outputResult(result, inputContext);
    }

    private static void executeDecisionService(String[] args) throws Exception {
        ParsedArgs parsed = parseArgs(args);
        
        if (parsed.mainDmnFile == null) {
            System.err.println("Error: DMN file path required");
            System.exit(1);
        }

        if (parsed.serviceName == null) {
            System.err.println("Error: Decision service name required. Use --service <name>");
            System.exit(1);
        }

        Map<String, Object> inputContext = mapper.readValue(
            parsed.inputJson, 
            new TypeReference<Map<String, Object>>() {}
        );

        DMNRuntime runtime = createRuntime(parsed.mainDmnFile, parsed.importPaths, parsed.autoImport, parsed.runtimeTypeCheck);
        DMNModel model = findMainModel(runtime, parsed.mainDmnFile, parsed.modelName);

        if (model == null) {
            outputError("Could not find main DMN model");
            System.exit(1);
        }

        // Verify decision service exists
        var decisionService = model.getDecisionServices().stream()
            .filter(ds -> ds.getName().equals(parsed.serviceName))
            .findFirst()
            .orElse(null);

        if (decisionService == null) {
            List<String> available = model.getDecisionServices().stream()
                .map(DMNNode::getName)
                .toList();
            outputError("Decision service '" + parsed.serviceName + "' not found. Available: " + available);
            System.exit(1);
        }

        DMNContext context = runtime.newContext();
        inputContext.forEach(context::set);

        DMNResult result = runtime.evaluateDecisionService(model, context, parsed.serviceName);
        outputResult(result, inputContext);
    }

    private static Object sanitizeValue(Object value) {
        if (value == null) return null;

        // Skip DMN internal objects that cause circular references
        String className = value.getClass().getName();
        if (className.contains("org.kie.dmn") &&
            (className.contains("Function") || className.contains("DecisionService"))) {
            return "[DMN " + value.getClass().getSimpleName() + "]";
        }

        // Handle collections
        if (value instanceof Map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> {
                Object sanitizedValue = sanitizeValue(v);
                if (sanitizedValue != null && !sanitizedValue.toString().startsWith("[DMN ")) {
                    sanitized.put(k.toString(), sanitizedValue);
                }
            });
            return sanitized;
        }

        if (value instanceof List) {
            return ((List<?>) value).stream()
                .map(DmnExecutor::sanitizeValue)
                .filter(v -> v != null && !v.toString().startsWith("[DMN "))
                .toList();
        }

        return value;
    }

    private static void outputResult(DMNResult result, Map<String, Object> inputContext) throws Exception {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", !result.hasErrors());

        if (result.hasErrors()) {
            output.put("errors", result.getMessages().stream()
                .filter(m -> m.getSeverity() == DMNMessage.Severity.ERROR)
                .map(m -> Map.of(
                    "message", m.getText(),
                    "type", m.getMessageType().toString()
                ))
                .toList());
        }

        // Extract decision results
        Map<String, Object> decisions = new LinkedHashMap<>();
        for (DMNDecisionResult dr : result.getDecisionResults()) {
            Object sanitized = sanitizeValue(dr.getResult());
            decisions.put(dr.getDecisionName(), Map.of(
                "result", sanitized != null ? sanitized : "null",
                "status", dr.getEvaluationStatus().toString()
            ));
        }
        output.put("decisions", decisions);

        // Include flat results for easy access
        Map<String, Object> results = new LinkedHashMap<>();
        result.getContext().getAll().forEach((k, v) -> {
            if (!inputContext.containsKey(k)) {
                Object sanitized = sanitizeValue(v);
                if (sanitized != null && !sanitized.toString().startsWith("[DMN ")) {
                    results.put(k, sanitized);
                }
            }
        });
        output.put("results", results);

        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    }

    private static void outputError(String message) throws Exception {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("success", false);
        errorResult.put("errors", List.of(message));
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorResult));
    }

    private static void outputErrors(List<DMNMessage> messages) throws Exception {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("success", false);
        errorResult.put("errors", messages.stream()
            .map(m -> m.getText())
            .toList());
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorResult));
    }

    private static void showModelInfo(String[] args) throws Exception {
        ParsedArgs parsed = parseArgs(args);
        
        if (parsed.mainDmnFile == null) {
            System.err.println("Error: DMN file path required");
            System.exit(1);
        }

        DMNRuntime runtime = createRuntime(parsed.mainDmnFile, parsed.importPaths, parsed.autoImport, parsed.runtimeTypeCheck);

        Map<String, Object> output = new LinkedHashMap<>();
        
        List<Map<String, Object>> models = new ArrayList<>();
        for (DMNModel model : runtime.getModels()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", model.getName());
            info.put("namespace", model.getNamespace());

            // Input data
            List<Map<String, String>> inputs = model.getInputs().stream()
                .map(i -> Map.of(
                    "name", i.getName(),
                    "type", i.getType() != null ? i.getType().getName() : "Any"
                ))
                .toList();
            info.put("inputs", inputs);

            // Decisions
            List<Map<String, String>> decisions = model.getDecisions().stream()
                .map(d -> Map.of(
                    "name", d.getName(),
                    "type", d.getResultType() != null ? d.getResultType().getName() : "Any"
                ))
                .toList();
            info.put("decisions", decisions);

            // Decision Services
            List<Map<String, Object>> decisionServices = model.getDecisionServices().stream()
                .map(ds -> {
                    Map<String, Object> dsInfo = new LinkedHashMap<>();
                    dsInfo.put("name", ds.getName());
                    
                    // Get input decisions (required inputs to the service)
                    if (ds.getDecisionService().getInputData() != null) {
                        dsInfo.put("inputData", ds.getDecisionService().getInputData().stream()
                            .map(ref -> {
                                String href = ref.getHref();
                                return href.contains("#") ? href.substring(href.indexOf("#") + 1) : href;
                            })
                            .toList());
                    }
                    
                    // Get input decisions
                    if (ds.getDecisionService().getInputDecision() != null) {
                        dsInfo.put("inputDecisions", ds.getDecisionService().getInputDecision().stream()
                            .map(ref -> {
                                String href = ref.getHref();
                                return href.contains("#") ? href.substring(href.indexOf("#") + 1) : href;
                            })
                            .toList());
                    }
                    
                    // Get output decisions
                    if (ds.getDecisionService().getOutputDecision() != null) {
                        dsInfo.put("outputDecisions", ds.getDecisionService().getOutputDecision().stream()
                            .map(ref -> {
                                String href = ref.getHref();
                                return href.contains("#") ? href.substring(href.indexOf("#") + 1) : href;
                            })
                            .toList());
                    }
                    
                    // Get encapsulated decisions
                    if (ds.getDecisionService().getEncapsulatedDecision() != null) {
                        dsInfo.put("encapsulatedDecisions", ds.getDecisionService().getEncapsulatedDecision().stream()
                            .map(ref -> {
                                String href = ref.getHref();
                                return href.contains("#") ? href.substring(href.indexOf("#") + 1) : href;
                            })
                            .toList());
                    }
                    
                    return dsInfo;
                })
                .toList();
            if (!decisionServices.isEmpty()) {
                info.put("decisionServices", decisionServices);
            }

            // Item definitions (custom types)
            List<Map<String, Object>> itemDefs = model.getItemDefinitions().stream()
                .map(id -> {
                    Map<String, Object> def = new LinkedHashMap<>();
                    def.put("name", id.getName());
                    if (id.getType() != null) {
                        def.put("type", id.getType().getName());
                    }
                    return def;
                })
                .toList();
            if (!itemDefs.isEmpty()) {
                info.put("itemDefinitions", itemDefs);
            }

            // Business knowledge models
            List<String> bkms = model.getBusinessKnowledgeModels().stream()
                .map(DMNNode::getName)
                .toList();
            if (!bkms.isEmpty()) {
                info.put("businessKnowledgeModels", bkms);
            }

            // Check for errors
            if (model.hasErrors()) {
                info.put("errors", model.getMessages().stream()
                    .filter(m -> m.getSeverity() == DMNMessage.Severity.ERROR)
                    .map(DMNMessage::getText)
                    .toList());
            }

            models.add(info);
        }
        
        output.put("modelsLoaded", models.size());
        output.put("models", models);

        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    }

    private static DMNModel findMainModel(DMNRuntime runtime, String mainDmnFile, String modelName) {
        if (modelName != null && !modelName.isEmpty()) {
            return runtime.getModels().stream()
                .filter(m -> m.getName().equals(modelName))
                .findFirst()
                .orElse(null);
        }
        
        String fileName = Path.of(mainDmnFile).getFileName().toString();
        String baseName = fileName.replace(".dmn", "").replace(".DMN", "");
        
        for (DMNModel model : runtime.getModels()) {
            if (model.getName().equalsIgnoreCase(baseName)) {
                return model;
            }
        }
        
        if (runtime.getModels().size() == 1) {
            return runtime.getModels().get(0);
        }
        
        return runtime.getModels().isEmpty() ? null : runtime.getModels().get(0);
    }

    private static DMNRuntime createRuntime(String mainDmnFile, List<String> importPaths, boolean autoImport, boolean runtimeTypeCheck) throws Exception {
        // KIE DMN runtime input type checking (enabled by default).
        // If disabled, the engine will be more permissive with input values.
        if (!runtimeTypeCheck) {
            System.setProperty("org.kie.dmn.runtime.typecheck", "false");
        }
        Path mainPath = Path.of(mainDmnFile);
        if (!Files.exists(mainPath)) {
            throw new FileNotFoundException("DMN file not found: " + mainDmnFile);
        }

        Set<Path> dmnFiles = new LinkedHashSet<>();
        dmnFiles.add(mainPath.toAbsolutePath());
        
        for (String importPath : importPaths) {
            Path p = Path.of(importPath);
            if (Files.isDirectory(p)) {
                try (Stream<Path> walk = Files.walk(p, 1)) {
                    walk.filter(f -> f.toString().toLowerCase().endsWith(".dmn"))
                        .map(Path::toAbsolutePath)
                        .forEach(dmnFiles::add);
                }
            } else if (Files.exists(p)) {
                dmnFiles.add(p.toAbsolutePath());
            }
        }
        
        if (autoImport) {
            Path parentDir = mainPath.toAbsolutePath().getParent();
            if (parentDir != null) {
                try (Stream<Path> walk = Files.list(parentDir)) {
                    walk.filter(f -> f.toString().toLowerCase().endsWith(".dmn"))
                        .map(Path::toAbsolutePath)
                        .forEach(dmnFiles::add);
                }
            }
        }

        List<Resource> resources = new ArrayList<>();
        for (Path dmnFile : dmnFiles) {
            Resource resource = ResourceFactory.newFileResource(dmnFile.toFile());
            resource.setSourcePath(dmnFile.toString());
            resource.setResourceType(ResourceType.DMN);
            resources.add(resource);
        }

        DMNRuntimeBuilder builder = DMNRuntimeBuilder.fromDefaults();
        
        return builder.buildConfiguration()
            .fromResources(resources)
            .getOrElseThrow(e -> new RuntimeException("Failed to build DMN runtime: " + e.getMessage(), e));
    }

    static class ParsedArgs {
        String mainDmnFile;
        String inputJson = "{}";
        String decisionName;
        String serviceName;
        String modelName;
        List<String> importPaths = new ArrayList<>();
        boolean autoImport = true;
        boolean runtimeTypeCheck = true;
    }

    private static ParsedArgs parseArgs(String[] args) throws IOException {
        ParsedArgs parsed = new ParsedArgs();
        
        int i = 1;
        while (i < args.length) {
            String arg = args[i];
            
            if (arg.equals("--import") || arg.equals("-i")) {
                if (i + 1 < args.length) {
                    parsed.importPaths.add(args[++i]);
                }
            } else if (arg.equals("--no-auto-import")) {
                parsed.autoImport = false;
            } else if (arg.equals("--no-typecheck")) {
                parsed.runtimeTypeCheck = false;
            } else if (arg.equals("--model") || arg.equals("-m")) {
                if (i + 1 < args.length) {
                    parsed.modelName = args[++i];
                }
            } else if (arg.equals("--decision") || arg.equals("-d")) {
                if (i + 1 < args.length) {
                    parsed.decisionName = args[++i];
                }
            } else if (arg.equals("--service") || arg.equals("-s")) {
                if (i + 1 < args.length) {
                    parsed.serviceName = args[++i];
                }
            } else if (parsed.mainDmnFile == null) {
                parsed.mainDmnFile = arg;
            } else if (parsed.inputJson.equals("{}")) {
                if ("-".equals(arg)) {
                    parsed.inputJson = new String(System.in.readAllBytes());
                } else {
                    parsed.inputJson = arg;
                }
            } else if (parsed.decisionName == null && parsed.serviceName == null) {
                parsed.decisionName = arg;
            }
            i++;
        }
        
        return parsed;
    }

    private static void printUsage() {
        System.out.println("""
            DMN Executor - Execute DMN 1.5 decision models using Apache KIE 10.1
            
            Supports DMN 1.1-1.5, FEEL expressions, imports, decision services, and boxed expressions.
            
            Usage:
              DmnExecutor.java execute <dmn-file> [input-json] [options]
              DmnExecutor.java service <dmn-file> [input-json] --service <name> [options]
              DmnExecutor.java info <dmn-file> [options]
              DmnExecutor.java help
            
            Commands:
              execute    Execute a DMN model (all decisions or specific decision/service)
              service    Execute a specific Decision Service (requires --service)
              info       Show model info including decisions, services, types, BKMs
              help       Show this help message
            
            Arguments:
              dmn-file      Path to the main DMN file
              input-json    JSON object with input values (use "-" for stdin)
            
            Options:
              -s, --service <name>   Execute a Decision Service by name
              -d, --decision <name>  Execute only a specific decision
              -m, --model <name>     Select model by name (when multiple loaded)
              -i, --import <path>    Add DMN file or directory to import
              --no-auto-import       Disable auto-importing from same directory
              --no-typecheck         Disable DMN runtime input type checking
            
            Decision Services:
              Decision Services encapsulate a subset of decisions, exposing only
              specified inputs and outputs. Use 'info' to see available services.
            
            Examples:
              # Execute all decisions
              jbang DmnExecutor.java execute model.dmn '{"x": 10}'
              
              # Execute a specific Decision Service
              jbang DmnExecutor.java execute model.dmn '{"x": 10}' --service "Pricing Service"
              
              # Or use the service command
              jbang DmnExecutor.java service model.dmn '{"x": 10}' --service "Pricing Service"
              
              # Show model info including decision services
              jbang DmnExecutor.java info model.dmn
              
              # Execute with stdin
              echo '{"age": 25}' | jbang DmnExecutor.java execute model.dmn -
            """);
    }
}
