package li.cil.oc.api.detail;

import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.fs.FileSystem;
import li.cil.oc.api.fs.Label;
import li.cil.oc.api.network.ManagedEnvironment;
import net.minecraft.util.ResourceLocation;

public interface FileSystemAPI {
    /**
     * Creates a new file system based on a mod-specific resource location where
     * the namespace refers to the mod and the resource path denotes a
     * (mandatory) subpath relative to that mod's assets directory.
     * <p/>
     * If {@code location} is stored in a JAR file, this will create a read-only
     * file system based on that JAR file. If {@code location} is stored in the
     * native file system, this will create a read-only file system from the the
     * location constructed as described above (relative to the root of the
     * namespace).
     * <p/>
     * If the specified path cannot be located, the creation fails and this
     * returns <tt>null</tt>.
     *
     * @param location the location where the file system's contents are stored.
     * @return a file system wrapping the specified resource.
     */
    FileSystem fromResource(ResourceLocation location);

    /**
     * Creates a new <em>writable</em> file system in the save folder.
     * <p/>
     * This will create a folder, if necessary, and create a writable virtual
     * file system based in that folder. The actual path is based in a sub-
     * folder of the save folder. The actual path is built like this:
     * <pre>"saves/" + WORLD_NAME + "/opencomputers/" + root</pre>
     * The first part may differ, in particular for servers.
     * <p/>
     * Usually the name will be the address of the node used to represent the
     * file system.
     * <p/>
     * Note that by default file systems are "buffered", meaning that any
     * changes made to them are only saved to disk when the world is saved. This
     * ensured that the file system contents do not go "out of sync" when the
     * game crashes, but introduces additional memory overhead, since all files
     * in the file system have to be kept in memory.
     *
     * @param root     the name of the file system.
     * @param capacity the amount of space in bytes to allow being used.
     * @param buffered whether data should only be written to disk when saving.
     * @return a file system wrapping the specified folder.
     */
    FileSystem fromSaveDirectory(String root, long capacity, boolean buffered);

    /**
     * Creates a new <em>writable</em> file system that resides in memory.
     * <p/>
     * Any contents created and written on this file system will be lost when
     * the node is removed from the network.
     * <p/>
     * This is used for computers' <tt>/tmp</tt> mount, for example.
     *
     * @param capacity the capacity of the file system.
     * @return a file system residing in memory.
     */
    FileSystem fromMemory(long capacity);

    /**
     * Wrap a file system retrieved via one of the <tt>from???</tt> methods to
     * make it read-only.
     *
     * @param fileSystem the file system to wrap.
     * @return the specified file system wrapped to be read-only.
     */
    FileSystem asReadOnly(final FileSystem fileSystem);

    /**
     * Creates a network node that makes the specified file system available via
     * the common file system driver.
     * <p/>
     * This can be useful for providing some data if you don't wish to implement
     * your own driver. Which will probably be most of the time. If you need
     * more control over the node, implement your own, and connect this one to
     * it. In that case you will have to forward any disk driver messages to the
     * node, though.
     * <p/>
     * The container parameter is used to give the file system some physical
     * relation to the world, for example this is used by hard drives to send
     * the disk event notifications to the client that are used to play disk
     * access sounds.
     * <p/>
     * The container may be <tt>null</tt>, if no such context can be provided.
     * <p/>
     * The access sound is the name of the sound effect to play when the file
     * system is accessed, for example by listing a directory or reading from
     * a file. It may be <tt>null</tt> to create a silent file system.
     * <p/>
     * The speed multiplier controls how fast read and write operations on the
     * file system are. It must be a value in [1,6], and controls the access
     * speed, with the default being one.
     * For reference, floppies are using the default, hard drives scale with
     * their tiers, i.e. a tier one hard drive uses speed two, tier three uses
     * speed four.
     *
     * @param fileSystem  the file system to wrap.
     * @param label       the label of the file system.
     * @param host        the tile entity containing the file system.
     * @param accessSound the name of the sound effect to play when the file
     *                    system is accessed. This has to be the fully
     *                    qualified resource name, e.g.
     *                    <tt>opencomputers:floppy_access</tt>.
     * @param speed       the speed multiplier for this file system.
     * @return the network node wrapping the file system.
     */
    ManagedEnvironment asManagedEnvironment(FileSystem fileSystem, Label label, EnvironmentHost host, String accessSound, int speed);

    /**
     * Creates a network node that makes the specified file system available via
     * the common file system driver.
     * <p/>
     * Creates a file system with the a read-only label and the specified
     * access sound and file system speed.
     *
     * @param fileSystem  the file system to wrap.
     * @param label       the read-only label of the file system.
     * @param host        the tile entity containing the file system.
     * @param accessSound the name of the sound effect to play when the file
     *                    system is accessed. This has to be the fully
     *                    qualified resource name, e.g.
     *                    <tt>opencomputers:floppy_access</tt>.
     * @param speed       the speed multiplier for this file system.
     * @return the network node wrapping the file system.
     */
    ManagedEnvironment asManagedEnvironment(FileSystem fileSystem, String label, EnvironmentHost host, String accessSound, int speed);

    /**
     * @deprecated Don't use this directly, use the wrapper in {@link li.cil.oc.api.FileSystem}.
     */
    @Deprecated
    ManagedEnvironment asManagedEnvironment(FileSystem fileSystem, Label label, EnvironmentHost host, String accessSound);

    /**
     * @deprecated Don't use this directly, use the wrapper in {@link li.cil.oc.api.FileSystem}.
     */
    @Deprecated
    ManagedEnvironment asManagedEnvironment(FileSystem fileSystem, String label, EnvironmentHost host, String accessSound);

    /**
     * @deprecated Don't use this directly, use the wrapper in {@link li.cil.oc.api.FileSystem}.
     */
    @Deprecated
    ManagedEnvironment asManagedEnvironment(FileSystem fileSystem, Label label);

    /**
     * @deprecated Don't use this directly, use the wrapper in {@link li.cil.oc.api.FileSystem}.
     */
    @Deprecated
    ManagedEnvironment asManagedEnvironment(FileSystem fileSystem, String label);

    /**
     * @deprecated Don't use this directly, use the wrapper in {@link li.cil.oc.api.FileSystem}.
     */
    @Deprecated
    ManagedEnvironment asManagedEnvironment(FileSystem fileSystem);
}