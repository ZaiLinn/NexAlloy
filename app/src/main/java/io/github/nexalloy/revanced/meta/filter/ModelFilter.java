package io.github.nexalloy.revanced.meta.filter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ported from Threads Sieve (dev.threads.sieve.ModelFilter).
 * Bindings verified against the original DEX names of Threads 450.0.0.51.78.
 */
public final class ModelFilter {
    private final ScriptFilter script;
    private final boolean homeS, homeT, replyS, replyT;
    private final Method immutable;
    private final Class<?> paging, thread, container, feedRow, packedMedia;

    public ModelFilter(
            ClassLoader loader, ScriptFilter script,
            boolean hs, boolean ht, boolean rs, boolean rt) throws Exception {
        this.script = script;
        homeS = hs;
        homeT = ht;
        replyS = rs;
        replyT = rt;
        immutable = Class.forName("X.9zw", false, loader).getDeclaredMethod("A00", Iterable.class);
        immutable.setAccessible(true);
        paging = Class.forName("X.1gO", false, loader);
        thread = Class.forName("X.2zY", false, loader);
        container = Class.forName("X.1Ky", false, loader);
        feedRow = Class.forName("X.3AD", false, loader);
        packedMedia = Class.forName("X.2ze", false, loader);
    }

    ModelFilter(
            ScriptFilter script, boolean hs, boolean ht, boolean rs, boolean rt,
            Method immutable, Class<?> paging, Class<?> thread, Class<?> container,
            Class<?> feedRow, Class<?> packedMedia) {
        this.script = script;
        homeS = hs;
        homeT = ht;
        replyS = rs;
        replyT = rt;
        this.immutable = immutable;
        this.paging = paging;
        this.thread = thread;
        this.container = container;
        this.feedRow = feedRow;
        this.packedMedia = packedMedia;
    }

    public Object homeState(Object state) throws Exception {
        if (!paging.isInstance(state)) return state;
        List<?> original = (List<?>) field(state, "A06");
        ArrayList<Object> result = new ArrayList<>();
        boolean changed = false;
        for (Object row : original) {
            if (!feedRow.isInstance(row)) {
                result.add(row);
                continue;
            }
            Object model = field(row, "A00");
            List<?> items = (List<?>) field(model, "A08");
            if (!items.isEmpty() && blockedItem(items.get(0), homeS, homeT)) {
                changed = true;
                continue;
            }
            List<?> clean = filterItems(items, null, true);
            if (clean != items) {
                model = copyThread(model, clean);
                row = construct(feedRow, new Object[]{model});
                changed = true;
            }
            result.add(row);
        }
        if (!changed) return state;
        return construct(paging, new Object[]{
                field(state, "A00"), field(state, "A01"), field(state, "A03"),
                field(state, "A02"), field(state, "A04"), field(state, "A05"),
                immutable.invoke(null, result), field(state, "A07"), field(state, "A08")});
    }

    public Object replies(Object value, String openedId) throws Exception {
        if (!(value instanceof List<?> original) || (!replyS && !replyT)) return value;
        ArrayList<Object> result = new ArrayList<>();
        boolean changed = false;
        for (Object row : original) {
            if (!container.isInstance(row)) {
                result.add(row);
                continue;
            }
            Object model = field(row, "A00");
            List<?> items = (List<?>) field(model, "A08");
            boolean containing = Objects.equals(field(row, "A01"), 0);
            List<?> clean = filterItems(items, openedId, containing && openedId == null);
            if (clean == items) {
                result.add(row);
                continue;
            }
            changed = true;
            if (!clean.isEmpty()) {
                result.add(construct(container, new Object[]{
                        copyThread(model, clean), field(row, "A01"), field(row, "A02")}));
            }
        }
        return changed ? result : value;
    }

    private List<?> filterItems(List<?> original, String openedId, boolean keepFirst) throws Exception {
        if (!replyS && !replyT) return original;
        ArrayList<Object> result = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < original.size(); i++) {
            Object item = original.get(i);
            Object media = media(item);
            boolean preserve =
                    (keepFirst && i == 0) || (media != null && openedId != null && openedId.equals(mediaId(media)));
            if (!preserve && blockedMedia(media, replyS, replyT)) changed = true;
            else result.add(item);
        }
        return changed ? result : original;
    }

    private Object copyThread(Object model, List<?> items) throws Exception {
        return construct(thread, new Object[]{
                field(model, "A00"), field(model, "A01"), field(model, "A02"),
                field(model, "A03"), field(model, "A04"), field(model, "A07"),
                field(model, "A06"), field(model, "A05"), items});
    }

    private Object media(Object item) throws Exception {
        Object value = field(item, "A04");
        return packedMedia.isInstance(value) ? field(value, "A00") : null;
    }

    private boolean blockedItem(Object item, boolean s, boolean t) throws Exception {
        return blockedMedia(media(item), s, t);
    }

    private boolean blockedMedia(Object media, boolean s, boolean t) throws Exception {
        if (media == null || (!s && !t)) return false;
        Object caption = call(media, "A33");
        return caption != null && script.blocks((CharSequence) call(caption, "Csa"), s, t);
    }

    public static String mediaId(Object media) throws Exception {
        return media == null ? null : (String) field(field(media, "A04"), "AAb");
    }

    public static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object construct(Class<?> type, Object[] args) throws Exception {
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterCount() == args.length) {
                c.setAccessible(true);
                return c.newInstance(args);
            }
        }
        throw new NoSuchMethodException(type.getName());
    }
}
