package org.javaseis.examples.plot;


import java.awt.*;
import java.lang.reflect.Method;
import java.util.EventListener;

public class Utils {
    private static String[] allPackages = { "java.awt.event.","javax.swing.event.","java.beans.","java.beans.beancontext."
            ,"java.awt.dnd.","javax.sql.","javax.naming.event.","javax.imageio.event.","javax.net.ssl.","javax.sound.midi."
            ,"javax.naming.ldap.","java.util.prefs.","javax.sound.sampled." };
    public static void removeListeners (Object o, String listenerName, String[] packages) {
        Class oc = o.getClass();
        try {
            Method getListenersMethod = oc.getMethod("get"+listenerName+"Listeners",(Class[])null);
            String[] p;
            if (packages == null) {
                p = allPackages;
            } else {
                p = packages;
            }

            // Find the listener Class
            Class listenerClass = null;
            int pIndex = 0;
            while (listenerClass == null && pIndex < p.length) {
                try {
                    listenerClass = Class.forName(p[pIndex]+listenerName+"Listener");
                } catch (Exception ex) {
                    /* Ignore */
                }
                pIndex++;
            }
            if (listenerClass == null) {
                return; // Couldn't find the class for this listener type
            }

            Method removeListenerMethod = oc.getMethod("remove"+listenerName+"Listener", new Class[] { listenerClass });
            if (getListenersMethod != null) {
                    //res1 = m.invoke(dto1,(Object[])null);

                Object els = getListenersMethod.invoke(o,(Object[])null);
                if (els instanceof EventListener[]) {
                    EventListener[] listeners = (EventListener[])els;
                    for (int i = 0; i < listeners.length; i++) {
                        removeListenerMethod.invoke(o, new Object[] { listeners[i] });
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private static String[] allListeners = { "Action","Adjustment","Ancestor","AWTEvent","BeanContextMembership","BeanContextServiceRevoked"
            ,"BeanContextServices","Caret","CellEditor","Change","Component","ConnectionEvent","Container","ControllerEvent"
            ,"Document","DragGesture","DragSource","DragSourceMotion","DropTarget","Focus","HandshakeCompleted","HierarchyBounds"
            ,"Hierarchy","Hyperlink","IIOReadProgress","IIOReadUpdate","IIOReadWarning","IIOWriteProgress","IIOWriteWarning"
            ,"InputMethod","InternalFrame","Item","Key","Line","ListData","ListSelection","MenuDragMouse","MenuKey","Menu"
            ,"MetaEvent","MouseInput","Mouse","MouseMotion","MouseWheel","NamespaceChange","Naming","NodeChange","ObjectChange"
            ,"PopupMenu","PreferenceChange","PropertyChange","RowSet","SSLSessionBinding","TableColumnModel","TableModel"
            ,"Text","TreeExpansion","TreeModel","TreeSelection","TreeWillExpand","UndoableEdit","UnsolicitedNotification"
            ,"VetoableChange","WindowFocus","Window","WindowState" } ;

    public static void removeAllListeners(Component c, String[] listeners, String[] packages) {
        String[] l;
        if (listeners == null) {
            l = allListeners;
        } else {
            l = listeners;
        }
        for (int i = 0; i < l.length; i++) {
            removeListeners(c,l[i],packages);
        }
    }

    public static void destroyComponent(Object o, String[] listeners, String[] packages) {
        if (o == null) {
            return;
        }
        if (o instanceof Container) {
            Component[] c = ((Container)o).getComponents();
            for (int i = 0; i < c.length; i++) {
              destroyComponent(c[i],listeners,packages);
            }
            ((Container)o).setLayout(null);
            ((Container)o).setFocusTraversalPolicy(null);
            ((Container)o).removeAll();
        }
        if (o instanceof javax.swing.JScrollPane) {
            ((javax.swing.JScrollPane)o).setViewportView(null);
        }
        if (o instanceof Component) {
            removeAllListeners((Component)o,listeners,packages);
        }
        if (o instanceof Window) {
            ((Window)o).dispose();
        }
    }

    public static void destroyComponent(Object o) {
      destroyComponent(o,null,null);
    }
}
