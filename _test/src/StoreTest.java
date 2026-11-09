import android.content.Context;

import com.etherealnymph.courseflow.Dates;
import com.etherealnymph.courseflow.LegacyImport;
import com.etherealnymph.courseflow.PrivateVault;
import com.etherealnymph.courseflow.Store;
import com.etherealnymph.courseflow.TimeTextParser;
import com.etherealnymph.courseflow.WeekSpec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;

public class StoreTest {
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        File dir = new File("courseflow-test-files");
        delete(dir);
        if (!dir.mkdirs()) throw new IllegalStateException("cannot create test dir");
        Context ctx = new Context(dir);
        Store store = Store.get(ctx);
        check(store.data.timetable.courses.size() == 16, "seed courses");
        check(store.data.timetable.timeSlots.size() == 11, "seed slots");
        check(store.data.notes.size() == 3, "seed notes");
        check(store.data.todos.size() == 7, "seed todos");
        check(store.data.alarms.size() == 4, "seed alarms");
        check(store.data.recordings.isEmpty(), "seed recordings");
        check(new File(dir, "courseflow.json").length() > 0, "state file saved");

        String json = store.timetableJson();
        Store.Timetable parsed = Store.parseTimetableText(json);
        check(parsed.courses.size() == 16, "roundtrip courses");
        check(parsed.timeSlots.size() == 11, "roundtrip slots");
        check(parsed.courses.get(0).teachers.size() == 1, "roundtrip teacher");
        Store.Course randomCourse = null;
        for (Store.Course c : parsed.courses) if ("D2701345.01".equals(c.code)) randomCourse = c;
        check(randomCourse != null && randomCourse.sessions.get(2).effectiveWeeks().size() == 2,
                "roundtrip week ranges");
        check(parsed.termStart.equals("2026-08-31"), "roundtrip term start");
        check(parsed.timeSlots.get(0).segments.get(0).start.equals("08:30"), "roundtrip first slot");

        String sample = new String(Files.readAllBytes(new File("samples/timetable-example.json").toPath()),
                StandardCharsets.UTF_8);
        Store.Timetable sampleTable = Store.parseTimetableText(sample);
        check(sampleTable.courses.size() == 3, "sample courses");
        check(sampleTable.courses.get(0).sessions.get(0).endSlotId.equals("p2"), "sample span");
        check(sampleTable.timeSlots.get(0).segments.get(0).start.equals("08:30"), "sample 11-slot start");

        check(WeekSpec.parse("单周 1-3, 7-13").size() == 2, "week parity ranges");
        check(WeekSpec.format(WeekSpec.parse("单周 1-3, 7-13")).equals("单周 1-3, 7-13"),
                "week parity format");
        check(WeekSpec.overlaps(WeekSpec.parse("1-10"), WeekSpec.parse("10-12")), "week overlap");

        LocalDate term = LocalDate.of(2026, 8, 31);
        check(Dates.weekOf(term, LocalDate.of(2026, 11, 6)) == 10, "week number");
        check(Dates.weekStart(term, 10).equals(LocalDate.of(2026, 11, 2)), "week monday");
        check(Dates.weekFull(5).equals("周五"), "weekday label");

        long parsedAt = TimeTextParser.parse("2026/11/9 16:15");
        check(parsedAt > 0, "time parser full");
        long tomorrow = TimeTextParser.parse("明天 12:30");
        check(tomorrow > 0, "time parser relative");
        check(TimeTextParser.parse("12v 7.9v 1.9A") == 0, "time parser false positive");

        String notes = store.notesJson();
        Store.NotesBundle bundle = Store.parseNotesText(notes);
        check(bundle.notes.size() == 3, "notes export");
        check(bundle.todos.size() == 7, "todos export");

        Store.Vault vault = PrivateVault.create("courseflow-1234");
        PrivateVault.Unlocked unlocked = PrivateVault.unlock(vault, "courseflow-1234");
        check(unlocked != null, "vault unlock");
        check(PrivateVault.unlock(vault, "wrong") == null, "vault wrong password");
        PrivateVault.Secret secret = new PrivateVault.Secret("s1", "秘密标题", "秘密正文", System.currentTimeMillis());
        Store.Attachment secretAttachment = new Store.Attachment();
        secretAttachment.id = "att1";
        secretAttachment.type = "image";
        secretAttachment.name = "秘密图片";
        secretAttachment.path = "/tmp/x.jpg";
        secret.attachments.add(secretAttachment);
        Store.PrivateNote encrypted = PrivateVault.encrypt(secret, unlocked.key);
        vault.notes.add(encrypted);
        PrivateVault.Unlocked again = PrivateVault.unlock(vault, "courseflow-1234");
        check(again != null && again.notes.size() == 1, "private note decrypt");
        check(again.notes.get(0).title.equals("秘密标题"), "private title");
        check(again.notes.get(0).attachments.size() == 1, "private attachment");
        check(!encrypted.cipher.contains("秘密"), "private ciphertext");

        Store.Vault changed = PrivateVault.changePassword(vault, again, "new-password");
        check(PrivateVault.unlock(changed, "new-password") != null, "changed password");
        check(PrivateVault.unlock(changed, "courseflow-1234") == null, "old password rejected");

        store.data.vault = vault;
        String withVault = store.notesJson();
        Store.NotesBundle privateBundle = Store.parseNotesText(withVault);
        check(privateBundle.vault != null, "encrypted vault export");
        check(privateBundle.vault.notes.size() == 1, "encrypted vault export notes");
        check(PrivateVault.unlock(privateBundle.vault, "courseflow-1234") != null, "exported vault unlock");

        Store.ImportResult result = store.applyNotesBundle(privateBundle, false);
        check(result.notes == 3, "merge note count");
        check(result.todos == 7, "merge todo count");

        String rawTodo = "{\"todo_list\":[{\"title\":\"练习册 P30-31\",\"deadline\":\"2026/11/9 00:00 (周日24:00)\"},"
                + "{\"title\":\"【调停课】本科生课程：G2700940.03电磁场理论\","
                + "\"timestamp\":\"2026/11/9 16:15\"},"
                + "{\"title\":\"大学物理考试\",\"content\":\"明天 12:30\"}]}";
        java.util.ArrayList<Store.Todo> importedTodos = LegacyImport.todos(rawTodo);
        check(importedTodos.size() == 3, "legacy todo import");
        check(importedTodos.get(0).dueAt > 0, "legacy deadline");
        check(importedTodos.get(2).dueAt > 0, "legacy relative time");

        String rawTable = "{\"timetable\":{\"星期一\":["
                + "{\"节次\":\"第一节\",\"课程\":\"电磁场理论\",\"教师\":\"陈益凯\","
                + "\"课程代码\":\"G2700940.03\",\"周次\":\"1-10, 12-17\",\"地点\":\"品学楼C203\"},"
                + "{\"节次\":\"第二节\",\"课程\":\"电磁场理论\",\"教师\":\"陈益凯\","
                + "\"课程代码\":\"G2700940.03\",\"周次\":\"1-10, 12-17\",\"地点\":\"品学楼C203\"},"
                + "{\"节次\":\"第三节\",\"备注\":\"停课\",\"周次\":\"4\"}],"
                + "\"星期三\":[{\"节次\":\"第1-2节\",\"课程\":\"半导体器件与物理\",\"教师\":\"刘志伟\","
                + "\"周次\":\"双周 9-16\",\"地点\":\"品学楼C404-A\"}]}}";
        Store.Timetable legacy = LegacyImport.timetable(rawTable);
        check(legacy.courses.size() == 2, "legacy timetable courses");
        check(legacy.courses.get(0).sessions.get(0).endSlotId.length() > 0, "legacy consecutive merge");
        check(legacy.timeSlots.size() == 11, "legacy 11 slots");
        check(legacy.courses.get(1).sessions.get(0).effectiveWeeks().get(0).parity.equals("even"),
                "legacy parity");

        boolean badRejected = false;
        try {
            Store.parseTimetableText("{\"termStartDate\":\"2026-09-07\",\"timeSlots\":[{\"id\":\"p1\",\"segments\":[{\"start\":\"08:00\",\"end\":\"09:00\"}]}],\"courses\":[{\"name\":\"x\",\"sessions\":[{\"dayOfWeek\":1,\"slotId\":\"ghost\"}]}]}");
        } catch (IllegalArgumentException e) {
            badRejected = true;
        }
        check(badRejected, "invalid session rejected");

        delete(dir);
        System.out.println("ALL " + checks + " CHECKS PASSED");
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + label);
        System.out.println("ok " + checks + " - " + label);
    }

    private static void delete(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
