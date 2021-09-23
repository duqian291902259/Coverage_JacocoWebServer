package site.duqian.spring.utils

import java.io.*

object CmdUtil {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val os = System.getProperty("os.name").toLowerCase()
            println("********** $os")

            if (os.contains("win")) {
            } else {
                runProcess("pwd")
                runProcess("http-server -p 8095")
            }
            println("********** start")
            //runProcess("git log")
            val rootDir = System.getProperty("user.dir") + File.separator
            val jarPath = "${rootDir}jacococli.jar"
            val execPath = "${rootDir}download/cc-android/dev_dq_#411671_coverage/8ab3adfcec889990a6db1fbaae361d59.ec"
            val classesPath = "${rootDir}jacoco/classes/"
            val srcPath = "${rootDir}jacoco/tempSrc/main/java/"
            val reportPath = "${rootDir}jacoco/report"
            runProcess("chmod -r 777 $jarPath")
            runProcess("cd $reportPath")

            val cmds = arrayOf(
                "java",
                "-jar",
                jarPath,
                "report",
                execPath,
                "--classfiles",
                classesPath,
                "--sourcefiles",
                srcPath,
                "--html",
                reportPath
            )
            runProcess(cmds)
            //win两个命令都能执行，todo-dq mac不行
            //runProcess("java -jar $jarPath report $execPath --classfiles $classesPath --sourcefiles $srcPath --html $reportPath")

            val currentBranchName = "dev_dq_#411671_coverage"
            //val process:Process = execute("git diff origin/master origin/${currentBranchName} --name-only")
            //val process:Process = execute("git clone https://github.com/duqian291902259/AndroidUI.git")
            //val text = getText(process)
            println("********** end")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runProcess(command: Array<String>): Boolean {
        try {
            val pro = Runtime.getRuntime().exec(command)
            val inputStream = pro.inputStream
            printLines("$command out:", inputStream)
            val errorStream = pro.errorStream
            printLines("$command err:", errorStream)
            pro.waitFor()
            println(command + " exitValue=${pro.exitValue()}")
            inputStream.close()
            errorStream.close()
            return true
        } catch (e: Exception) {
            println("runProcess $e")
        }
        return false
    }

    @JvmStatic
    @Throws(Exception::class)
    private fun printLines(cmd: String, ins: InputStream) {
        var line: String? = ""
        val `in` = BufferedReader(
            InputStreamReader(ins)
        )
        while (`in`.readLine().also { line = it } != null) {
            println("$cmd $line")
        }
        `in`.close()
    }

    @JvmStatic
    fun runProcess(command: String): Boolean {
        try {
            val pro = Runtime.getRuntime().exec(command)
            val inputStream = pro.inputStream
            printLines("$command out:", inputStream)
            val errorStream = pro.errorStream
            printLines("$command err:", errorStream)
            pro.waitFor()
            println(command + " exitValue=${pro.exitValue()}")
            inputStream.close()
            errorStream.close()
            return true
        } catch (e: Exception) {
            println("runProcess $e")
        }
        return false
    }

    @Throws(IOException::class)
    @JvmStatic
    fun execute(self: String?): Process {
        return Runtime.getRuntime().exec(self)
    }

    @Throws(IOException::class)
    @JvmStatic
    fun getText(self: Process): String {
        val text: String = getText(BufferedReader(InputStreamReader(self.inputStream)))
        closeStreams(self)
        return text
    }

    @Throws(IOException::class)
    fun getText(reader: BufferedReader?): String {
        var bufferedReader = reader
        val answer = StringBuilder()
        val charBuffer = CharArray(8192)
        var nbCharRead: Int
        try {
            while (bufferedReader!!.read(charBuffer).also { nbCharRead = it } != -1) {
                answer.append(charBuffer, 0, nbCharRead)
            }
            val temp = bufferedReader
            bufferedReader = null
            temp.close()
        } finally {
            tryClose(bufferedReader, true) // ignore result
        }
        return answer.toString()
    }

    fun tryClose(closeable: AutoCloseable?, logWarning: Boolean): Throwable? {
        var thrown: Throwable? = null
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: Exception) {
                thrown = e
                if (logWarning) {
                    println("tryClose error $e")
                }
            }
        }
        return thrown
    }

    fun closeStreams(self: Process) {
        try {
            self.errorStream.close()
        } catch (ignore: IOException) {
        }
        try {
            self.inputStream.close()
        } catch (ignore: IOException) {
        }
        try {
            self.outputStream.close()
        } catch (ignore: IOException) {
        }
    }
}