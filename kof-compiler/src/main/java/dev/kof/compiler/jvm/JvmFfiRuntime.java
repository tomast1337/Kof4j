package dev.kof.compiler.jvm;

/**
 * FFI (R3, TIER 2.1.4/2.1.6): downcall JVM-first via FFM
 * ({@code java.lang.foreign}) para o target JVM. Mantido fora de
 * {@code JvmRuntime} para a regra de ≤500 linhas/classe.
 */
final class JvmFfiRuntime {

    private JvmFfiRuntime() {}

    static String source() {
        // O source gerado é compilado IN-PROCESS (ToolProvider) pelo MESMO JDK
        // que roda o compilador — e o gate de preview em JvmRuntime usa a mesma
        // condição. JDK 21 (FFM preview): Arena.allocateUtf8String(String);
        // JDK 22+ (FFM final, JEP 454): Arena.allocateFrom(String).
        String alloc = Runtime.version().feature() < 22 ? "allocateUtf8String" : "allocateFrom";
        return FORMATTED.formatted(alloc);
    }

    private static final String FORMATTED = """
                public static int kof_ffi_i(String lib, String name, int a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.global();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_INT,
                                        java.lang.foreign.ValueLayout.JAVA_INT));
                        return (int) handle.invoke(a);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_i: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static int kof_ffi_si(String lib, String name, String a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_INT,
                                        java.lang.foreign.ValueLayout.ADDRESS));
                        java.lang.foreign.MemorySegment seg = arena.%s(a);
                        return (int) handle.invoke(seg);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_si: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static double kof_ffi_dd(String lib, String name, double a) {
                    try {
                        java.lang.foreign.Arena arena = java.lang.foreign.Arena.global();
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
                        java.lang.invoke.MethodHandle handle = linker.downcallHandle(
                                lookup.find(name).orElseThrow(),
                                java.lang.foreign.FunctionDescriptor.of(
                                        java.lang.foreign.ValueLayout.JAVA_DOUBLE,
                                        java.lang.foreign.ValueLayout.JAVA_DOUBLE));
                        return (double) handle.invoke(a);
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_dd: " + lib + "::" + name + " failed: "
                                + t.getMessage(), t);
                    }
                }

                public static java.lang.Object kof_ffi_call(String lib, String name, String signature,
                        java.lang.Object[] args) {
                    return kof_ffi_call0(lib, name, signature, args);
                }

                public static void kof_ffi_call_void(String lib, String name, String signature,
                        java.lang.Object[] args) {
                    kof_ffi_call0(lib, name, signature, args);
                }

                private static java.lang.Object kof_ffi_call0(String lib, String name, String signature,
                        java.lang.Object[] args) {
                    // Keep the loaded library alive for the process lifetime. A
                    // confined arena here would unload raylib after each call,
                    // invalidating its global GL function table before CloseWindow.
                    java.lang.foreign.Arena arena = java.lang.foreign.Arena.global();
                    try {
                        int arrow = signature.indexOf("->");
                        if (arrow < 0) throw new IllegalArgumentException("invalid FFI signature: " + signature);
                        String argKinds = signature.substring(0, arrow);
                        String retKind = signature.substring(arrow + 2);
                        java.util.List<java.lang.foreign.MemoryLayout> layouts = new java.util.ArrayList<>();
                        java.util.List<java.lang.Object> values = new java.util.ArrayList<>();
                        for (int i = 0; i < argKinds.length(); i++) {
                            char kind = argKinds.charAt(i);
                            java.lang.Object value = args[i];
                            switch (kind) {
                                case 'I' -> {
                                    layouts.add(java.lang.foreign.ValueLayout.JAVA_INT);
                                    if (value instanceof java.lang.Boolean b) value = b ? 1 : 0;
                                    else if (value instanceof java.lang.Character c) value = (int) c;
                                    else if (value instanceof java.lang.Byte || value instanceof java.lang.Short)
                                        value = ((java.lang.Number) value).intValue();
                                }
                                case 'B' -> {
                                    layouts.add(java.lang.foreign.ValueLayout.JAVA_BOOLEAN);
                                    if (value instanceof java.lang.Number n) value = n.intValue() != 0;
                                }
                                case 'J' -> layouts.add(java.lang.foreign.ValueLayout.JAVA_LONG);
                                case 'F' -> layouts.add(java.lang.foreign.ValueLayout.JAVA_FLOAT);
                                case 'D' -> layouts.add(java.lang.foreign.ValueLayout.JAVA_DOUBLE);
                                case 'S' -> {
                                    layouts.add(java.lang.foreign.ValueLayout.ADDRESS);
                                    value = arena.%1$s((String) value);
                                }
                                default -> throw new IllegalArgumentException("unsupported FFI argument kind: " + kind);
                            }
                            values.add(value);
                        }
                        java.lang.foreign.SymbolLookup lookup = lib.isEmpty()
                                ? java.lang.foreign.SymbolLookup.loaderLookup()
                                : java.lang.foreign.SymbolLookup.libraryLookup(lib, arena);
                        java.lang.foreign.MemoryLayout[] layoutArray =
                                layouts.toArray(new java.lang.foreign.MemoryLayout[0]);
                        java.lang.foreign.FunctionDescriptor fd = "V".equals(retKind)
                                ? java.lang.foreign.FunctionDescriptor.ofVoid(layoutArray)
                                : java.lang.foreign.FunctionDescriptor.of(returnLayout(retKind), layoutArray);
                        java.lang.invoke.MethodHandle handle = java.lang.foreign.Linker.nativeLinker()
                                .downcallHandle(lookup.find(name).orElseThrow(), fd);
                        java.lang.Object result = handle.invokeWithArguments(values);
                        if ("B".equals(retKind)) {
                            if (result instanceof java.lang.Boolean) return result;
                            return ((java.lang.Number) result).intValue() != 0;
                        }
                        return result;
                    } catch (Throwable t) {
                        throw new RuntimeException("kof_ffi_call: " + lib + "::" + name
                                + " (" + signature + ") failed: " + t.getMessage(), t);
                    }
                }

                private static java.lang.foreign.ValueLayout returnLayout(String kind) {
                    return switch (kind) {
                        case "I" -> java.lang.foreign.ValueLayout.JAVA_INT;
                        case "B" -> java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
                        case "J" -> java.lang.foreign.ValueLayout.JAVA_LONG;
                        case "F" -> java.lang.foreign.ValueLayout.JAVA_FLOAT;
                        case "D" -> java.lang.foreign.ValueLayout.JAVA_DOUBLE;
                        default -> throw new IllegalArgumentException("unsupported FFI return kind: " + kind);
                    };
                }

    """;
}
