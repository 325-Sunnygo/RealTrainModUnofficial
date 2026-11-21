package net.neoforged.neoforge.network.event;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** シム: エントリポイントが生成して登録メソッドへ渡す。 */
public class RegisterPayloadHandlersEvent extends Event {
    private final PayloadRegistrar registrar = new PayloadRegistrar();

    public PayloadRegistrar registrar(String version) {
        return registrar;
    }

    public PayloadRegistrar registrar() {
        return registrar;
    }
}
