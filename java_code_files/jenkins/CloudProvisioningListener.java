package hudson.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.queue.CauseOfBlockage;
import jenkins.model.Jenkins;

import java.util.Collection;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

/**
 * Allows extensions to be notified of events in any {@link Cloud} and to prevent
 * provisioning from a {@link Cloud}.
 *
 * @author Ryan Campbell
 * @since 1.520
 */
public abstract class CloudProvisioningListener implements ExtensionPoint {


    /**
     * Allows extensions to prevent a cloud from provisioning.
     *
     * Return null to allow provisioning, or non-null to prevent it.
     *
     * @param cloud The cloud being provisioned from.
     * @param label The label which requires additional capacity. IE,
     *              the {@link NodeProvisioner#label}.
     *              May be null if provisioning for unlabeled builds.
     * @param numExecutors The number of executors needed.
     *
     * @return <code>null</code> if provisioning can proceed, or a
     * {@link CauseOfBlockage} reason why it cannot be provisioned.
     */
    public CauseOfBlockage canProvision(Cloud cloud, Label label, int numExecutors) {
        return null;
    }

    /**
     * Called after a cloud has returned a PlannedNode, but before
     * that node is necessarily ready for connection.
     *
     * @param cloud the cloud doing the provisioning
     * @param label the label which requires additional capacity. IE,
     *              the {@link NodeProvisioner#label}
     *              May be null if provisioning for unlabeled builds.
     * @param plannedNodes the planned nodes
     *
     */
    public void onStarted(Cloud cloud, Label label, Collection<NodeProvisioner.PlannedNode> plannedNodes) {

    }

    /**
     * Called when the {@link NodeProvisioner.PlannedNode#future} completes.
     *
     * @param plannedNode the plannedNode which resulted in the <code>node</code> being provisioned
     * @param node the node which has been provisioned by the cloud
     */
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {

    }

    /**
     * Called when the <code>node</code>is fully connected in the Jenkins.
     *
     * @param plannedNode the plannedNode which resulted in the <code>node</code> being provisioned
     * @param node the node which has been provisioned by the cloud
     *
     * @since 2.37
     */
    public void onCommit(@Nonnull NodeProvisioner.PlannedNode plannedNode, @Nonnull Node node) {
        // Noop by default
    }

    /**
     * Called when {@link NodeProvisioner.PlannedNode#future} {@link Future#get()} throws an exception.
     *
     * @param plannedNode the planned node which failed to provision
     * @param t the exception
     */
    public void onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {

    }

    /**
     * Called when {@link Jenkins#addNode(Node)} throws an exception.
     *
     * @param plannedNode the plannedNode which resulted in the <code>node</code> being provisioned
     * @param node the node which has been provisioned by the cloud
     * @param t the exception
     *
     * @since 2.37
     */
    public void onRollback(@Nonnull NodeProvisioner.PlannedNode plannedNode, @Nonnull Node node,
                           @Nonnull Throwable t) {
        // Noop by default
    }

    /**
     * All the registered {@link CloudProvisioningListener}s.
     */
    public static ExtensionList<CloudProvisioningListener> all() {
        return ExtensionList.lookup(CloudProvisioningListener.class);
    }

}

