package ch.batbern.shared.test;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds cron jobs that do not state their time zone (#1222).
 *
 * <p>A {@code @Scheduled(cron = ...)} without {@code zone} runs in the JVM's default zone, which is
 * UTC on ECS (no {@code TZ} is set, on purpose: the code states the zone). A job meant for 08:00 in
 * Bern then runs at 09:00 or 10:00 Swiss time. Every cron must say {@code zone = "Europe/Zurich"}.
 * {@code fixedRate} / {@code fixedDelay} jobs have no wall-clock time and are not checked.
 *
 * <p>Reads the compiled classes, so a multi-line annotation, {@code @Schedules} and an annotation
 * meta-annotated with {@code @Scheduled} are all seen. Classes are loaded without being initialised.
 */
public final class ScheduledZoneGuard {

    /** The zone every cron of BATbern runs in. */
    public static final String ZONE = "Europe/Zurich";

    private ScheduledZoneGuard() {
    }

    /**
     * @param basePackages e.g. {@code ch.batbern.events}
     * @return one line per cron without {@code zone = "Europe/Zurich"}, e.g.
     *     {@code "ch.batbern.events.service.UploadExpiryJob#purgeExpired cron='0 15 2 * * *' zone=''"};
     *     empty if every cron states the zone
     */
    public static List<String> cronsWithoutZurichZone(String... basePackages) {
        Set<String> found = new TreeSet<>();
        for (String basePackage : basePackages) {
            for (Class<?> type : classesIn(basePackage, found)) {
                Method[] methods;
                try {
                    methods = type.getDeclaredMethods();
                } catch (LinkageError e) {
                    found.add(type.getName() + ": methods not readable (" + e + ")");
                    continue;
                }
                for (Method method : methods) {
                    for (Scheduled scheduled : AnnotatedElementUtils.findMergedRepeatableAnnotations(method,
                            Scheduled.class)) {
                        if (!scheduled.cron().isEmpty() && !ZONE.equals(scheduled.zone())) {
                            found.add(type.getName() + "#" + method.getName() + " cron='" + scheduled.cron()
                                    + "' zone='" + scheduled.zone() + "'");
                        }
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Every class of the package and its subpackages; a class that cannot be loaded is a finding. */
    private static List<Class<?>> classesIn(String basePackage, Set<String> found) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
        ClassLoader loader = ScheduledZoneGuard.class.getClassLoader();
        List<Class<?>> classes = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(
                    "classpath*:" + basePackage.replace('.', '/') + "/**/*.class");
            if (resources.length == 0) {
                throw new IllegalArgumentException("No classes found in " + basePackage);
            }
            for (Resource resource : resources) {
                String name = readers.getMetadataReader(resource).getClassMetadata().getClassName();
                try {
                    classes.add(Class.forName(name, false, loader));
                } catch (ClassNotFoundException | LinkageError e) {
                    found.add(name + ": not loadable (" + e + ")");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return classes;
    }
}
