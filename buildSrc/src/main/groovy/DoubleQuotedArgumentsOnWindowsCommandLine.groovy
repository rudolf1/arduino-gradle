
public class DoubleQuotedArgumentsOnWindowsCommandLine {
    public static String fixArgument(String argument) {
        // Brutal hack to workaround windows command line parsing.
        // http://stackoverflow.com/questions/5969724/java-runtime-exec-fails-to-escape-characters-properly
        // http://msdn.microsoft.com/en-us/library/a1y7w461.aspx
        // http://bugs.sun.com/view_bug.do?bug_id=6468220
        // http://bugs.sun.com/view_bug.do?bug_id=6518827
        if (argument.contains("\"") && isWindowsOS()) {
            argument = argument.replace("\"", "\\\"");
        }
        return argument;
    }

    public static boolean isWindowsOS() {
        Properties props = System.getProperties();
        String os = props.get("os.name").toString();
        return os.contains("Windows");
    }
}
