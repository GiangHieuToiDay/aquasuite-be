package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryStorageConfigurationTest {
    @TempDir Path directory;

    @Test void defaultStoragePersistsAcrossInstancesAndDeletes() {
        new ApplicationContextRunner()
            .withPropertyValues("aquasuite.delivery.photo-directory=" + directory)
            .withUserConfiguration(UnavailableDeliveryPhotoStorage.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(DeliveryPhotoStorage.class);
            var storage = ctx.getBean(DeliveryPhotoStorage.class);
            var stored = storage.store(new byte[]{1, 2, 3}, "image/png");
            var reopened = new LocalDeliveryPhotoStorage(directory);
            assertThat(reopened.read(stored.key()).bytes()).containsExactly(1, 2, 3);
            assertThat(reopened.read(stored.key()).mimeType()).isEqualTo("image/png");
            reopened.delete(stored.key());
            assertThatThrownBy(() -> storage.read(stored.key())).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test void rejectsPathsOutsidePhotoDirectory() {
        var storage = new LocalDeliveryPhotoStorage(directory);
        assertThatThrownBy(() -> storage.read("../outside.png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("../outside.png")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void realAdapterCanReplaceDefaultWithoutChangingService() {
        var real = mock(DeliveryPhotoStorage.class);
        new ApplicationContextRunner().withBean(DeliveryPhotoStorage.class, () -> real)
            .withUserConfiguration(UnavailableDeliveryPhotoStorage.class).run(ctx -> {
                assertThat(ctx).hasSingleBean(DeliveryPhotoStorage.class);
                assertThat(ctx.getBean(DeliveryPhotoStorage.class)).isSameAs(real);
            });
    }
}
