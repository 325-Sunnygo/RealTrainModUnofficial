package net.neoforged.neoforge.common.world.chunk;

import net.neoforged.bus.api.Event;

/** シム: チケットコントローラの登録イベント。Fabric では登録先が無いので受けるだけ。 */
public class RegisterTicketControllersEvent extends Event {
    public void register(TicketController controller) {
    }
}
