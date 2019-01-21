/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Seiji Sogabe, Martin Eigenbrodt, Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.Extension;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Descriptor.FormException;
import hudson.model.listeners.ItemListener;
import hudson.search.CollectionSearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.views.ListViewColumn;
import hudson.views.ViewJobFilter;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.CheckForNull;
import javax.annotation.concurrent.GuardedBy;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Displays {@link Job}s in a flat list view.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListView extends View implements DirectlyModifiableView {

    /**
     * List of job names. This is what gets serialized.
     */
    @GuardedBy("this")
    /*package*/ /*almost-final*/ SortedSet<String> jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);
    
    private DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> jobFilters;

    private DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns;

    /**
     * Include regex string.
     */
    private String includeRegex;
    
    /**
     * Whether to recurse in ItemGroups
     */
    private boolean recurse;
    
    /**
     * Compiled include pattern from the includeRegex string.
     */
    private transient Pattern includePattern;

    /**
     * Filter by enabled/disabled status of jobs.
     * Null for no filter, true for enabled-only, false for disabled-only.
     */
    private Boolean statusFilter;

    @DataBoundConstructor
    public ListView(String name) {
        super(name);
        initColumns();
        initJobFilters();
    }

    public ListView(String name, ViewGroup owner) {
        this(name);
        this.owner = owner;
    }

    /**
     * Sets the columns of this view.
     */
    @DataBoundSetter
    public void setColumns(List<ListViewColumn> columns) throws IOException {
        this.columns.replaceBy(columns);
    }

    private Object readResolve() {
        if(includeRegex!=null) {
            try {
                includePattern = Pattern.compile(includeRegex);
            } catch (PatternSyntaxException x) {
                includeRegex = null;
                OldDataMonitor.report(this, Collections.<Throwable>singleton(x));
            }
        }
        synchronized(this) {
            if (jobNames == null) {
                jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);
            }
        }
        initColumns();
        initJobFilters();
        return this;
    }

    protected void initColumns() {
        if (columns == null)
            columns = new DescribableList<ListViewColumn, Descriptor<ListViewColumn>>(this,
                    ListViewColumn.createDefaultInitialColumnList(getClass())
            );
    }

    protected void initJobFilters() {
        if (jobFilters == null)
            jobFilters = new DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>>(this);
    }

    /**
     * Used to determine if we want to display the Add button.
     */
    public boolean hasJobFilterExtensions() {
    	return !ViewJobFilter.all().isEmpty();
    }

    public DescribableList<ViewJobFilter, Descriptor<ViewJobFilter>> getJobFilters() {
    	return jobFilters;
    }

    @Override
    public DescribableList<ListViewColumn, Descriptor<ListViewColumn>> getColumns() {
        return columns;
    }


    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     */
    @Override
    public List<TopLevelItem> getItems() {
        return getItems(this.recurse);
     }

    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     * @param recurse {@code false} not to recurse in ItemGroups
     * true to recurse in ItemGroups
     */
    private List<TopLevelItem> getItems(boolean recurse) {
        SortedSet<String> names;
        List<TopLevelItem> items = new ArrayList<TopLevelItem>();

        synchronized (this) {
            names = new TreeSet<String>(jobNames);
        }

        ItemGroup<? extends TopLevelItem> parent = getOwner().getItemGroup();
        List<TopLevelItem> parentItems = new ArrayList<TopLevelItem>(parent.getItems());
        includeItems(parent, parentItems, names);

        Boolean statusFilter = this.statusFilter; // capture the value to isolate us from concurrent update
        Iterable<? extends TopLevelItem> candidates;
        if (recurse) {
            candidates = parent.getAllItems(TopLevelItem.class);
        } else {
            candidates = parent.getItems();
        }
        for (TopLevelItem item : candidates) {
            if (!names.contains(item.getRelativeNameFrom(getOwner().getItemGroup()))) continue;
            // Add if no status filter or filter matches enabled/disabled status:
            if(statusFilter == null || !(item instanceof ParameterizedJobMixIn.ParameterizedJob) // TODO or better to call the more generic Job.isBuildable?
                              || ((ParameterizedJobMixIn.ParameterizedJob)item).isDisabled() ^ statusFilter)
                items.add(item);
        }

        // check the filters
        Iterable<ViewJobFilter> jobFilters = getJobFilters();
        List<TopLevelItem> allItems = new ArrayList<TopLevelItem>(parentItems);
        if (recurse) allItems = expand(allItems, new ArrayList<TopLevelItem>());
    	for (ViewJobFilter jobFilter: jobFilters) {
    		items = jobFilter.filter(items, allItems, this);
    	}
        // for sanity, trim off duplicates
        items = new ArrayList<TopLevelItem>(new LinkedHashSet<TopLevelItem>(items));
        
        return items;
    }

    @Override
    public SearchIndexBuilder makeSearchIndex() {
        SearchIndexBuilder sib = new SearchIndexBuilder().addAllAnnotations(this);
        sib.add(new CollectionSearchIndex<TopLevelItem>() {// for jobs in the view
            protected TopLevelItem get(String key) { return getItem(key); }
            protected Collection<TopLevelItem> all() { return getItems(); }
            @Override
            protected String getName(TopLevelItem o) {
                // return the name instead of the display for suggestion searching
                return o.getName();
            }
        });
        // add the display name for each item in the search index
        addDisplayNamesToSearchIndex(sib, getItems(true));
        return sib;
    }

    private List<TopLevelItem> expand(Collection<TopLevelItem> items, List<TopLevelItem> allItems) {
        for (TopLevelItem item : items) {
            if (item instanceof ItemGroup) {
                ItemGroup<? extends Item> ig = (ItemGroup<? extends Item>) item;
                expand(Util.filter(ig.getItems(), TopLevelItem.class), allItems);
            }
            allItems.add(item);
        }
        return allItems;
    }
    
    @Override
    public boolean contains(TopLevelItem item) {
      return getItems().contains(item);
    }
    
    private void includeItems(ItemGroup<? extends TopLevelItem> root, Collection<? extends Item> parentItems, SortedSet<String> names) {
        if (includePattern != null) {
            for (Item item : parentItems) {
                if (recurse && item instanceof ItemGroup) {
                    ItemGroup<?> ig = (ItemGroup<?>) item;
                    includeItems(root, ig.getItems(), names);
                }
                if (item instanceof TopLevelItem) {
                    String itemName = item.getRelativeNameFrom(root);
                    if (includePattern.matcher(itemName).matches()) {
                        names.add(itemName);
                    }
                }
            }
        }
    }
    
    public synchronized boolean jobNamesContains(TopLevelItem item) {
        if (item == null) return false;
        return jobNames.contains(item.getRelativeNameFrom(getOwner().getItemGroup()));
    }

    /**
     * Adds the given item to this view.
     *
     * @since 1.389
     */
    @Override
    public void add(TopLevelItem item) throws IOException {
        synchronized (this) {
            jobNames.add(item.getRelativeNameFrom(getOwner().getItemGroup()));
        }
        save();
    }

    /**
     * Removes given item from this view.
     *
     * @since 1.566
     */
    @Override
    public boolean remove(TopLevelItem item) throws IOException {
        synchronized (this) {
            String name = item.getRelativeNameFrom(getOwner().getItemGroup());
            if (!jobNames.remove(name)) return false;
        }
        save();
        return true;
    }

    public String getIncludeRegex() {
        return includeRegex;
    }
    
    public boolean isRecurse() {
        return recurse;
    }
    
    /**
     * @since 1.568
     */
    public void setRecurse(boolean recurse) {
        this.recurse = recurse;
    }

    /**
     * Filter by enabled/disabled status of jobs.
     * Null for no filter, true for enabled-only, false for disabled-only.
     */
    public Boolean getStatusFilter() {
        return statusFilter;
    }

    /**
     * Determines the initial state of the checkbox.
     *
     * @return true when the view is empty or already contains jobs specified by name.
     */
    @Restricted(NoExternalUse.class) // called from newJob_button-bar view
    @SuppressWarnings("unused") // called from newJob_button-bar view
    public boolean isAddToCurrentView() {
        synchronized(this) {
            return !jobNames.isEmpty() || // There are already items in this view specified by name
                    (jobFilters.isEmpty() && includePattern == null) // No other way to include items is used
                    ;
        }
    }

    private boolean needToAddToCurrentView(StaplerRequest req) throws ServletException {
        String json = req.getParameter("json");
        if (json != null && json.length() > 0) {
            // Submitted via UI
            JSONObject form = req.getSubmittedForm();
            return form.has("addToCurrentView") && form.getBoolean("addToCurrentView");
        } else {
            // Submitted via API
            return true;
        }
    }

    @Override
    @RequirePOST
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        ItemGroup<? extends TopLevelItem> ig = getOwner().getItemGroup();
        if (ig instanceof ModifiableItemGroup) {
            TopLevelItem item = ((ModifiableItemGroup<? extends TopLevelItem>)ig).doCreateItem(req, rsp);
            if (item!=null) {
                if (needToAddToCurrentView(req)) {
                    synchronized (this) {
                        jobNames.add(item.getRelativeNameFrom(getOwner().getItemGroup()));
                    }
                    owner.save();
                }
            }
            return item;
        }
        return null;
    }

    @Override
    @RequirePOST
    public HttpResponse doAddJobToView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if(name==null)
            throw new Failure("Query parameter 'name' is required");

        TopLevelItem item = resolveName(name);
        if (item == null)
            throw new Failure("Query parameter 'name' does not correspond to a known item");

        if (contains(item)) return HttpResponses.ok();

        add(item);
        owner.save();

        return HttpResponses.ok();
    }

    @Override
    @RequirePOST
    public HttpResponse doRemoveJobFromView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if(name==null)
            throw new Failure("Query parameter 'name' is required");

        TopLevelItem item = resolveName(name);
        if (item==null)
            throw new Failure("Query parameter 'name' does not correspond to a known and readable item");

        if (remove(item))
            owner.save();

        return HttpResponses.ok();
    }

    private @CheckForNull TopLevelItem resolveName(String name) {
        TopLevelItem item = getOwner().getItemGroup().getItem(name);
        if (item == null) {
            name = Items.getCanonicalName(getOwner().getItemGroup(), name);
            item = Jenkins.getInstance().getItemByFullName(name, TopLevelItem.class);
        }
        return item;
    }

    /**
     * Handles the configuration submission.
     *
     * Load view-specific properties here.
     */
    @Override
    protected void submit(StaplerRequest req) throws ServletException, FormException, IOException {
        JSONObject json = req.getSubmittedForm();
        synchronized (this) {
            recurse = json.optBoolean("recurse", true);
            jobNames.clear();
            Iterable<? extends TopLevelItem> items;
            if (recurse) {
                items = getOwner().getItemGroup().getAllItems(TopLevelItem.class);
            } else {
                items = getOwner().getItemGroup().getItems();
            }
            for (TopLevelItem item : items) {
                String relativeNameFrom = item.getRelativeNameFrom(getOwner().getItemGroup());
                if(req.getParameter(relativeNameFrom)!=null) {
                    jobNames.add(relativeNameFrom);
                }
            }
        }

        setIncludeRegex(req.getParameter("useincluderegex") != null ? req.getParameter("includeRegex") : null);

        if (columns == null) {
            columns = new DescribableList<ListViewColumn,Descriptor<ListViewColumn>>(this);
        }
        columns.rebuildHetero(req, json, ListViewColumn.all(), "columns");
        
        if (jobFilters == null) {
        	jobFilters = new DescribableList<ViewJobFilter,Descriptor<ViewJobFilter>>(this);
        }
        jobFilters.rebuildHetero(req, json, ViewJobFilter.all(), "jobFilters");

        String filter = Util.fixEmpty(req.getParameter("statusFilter"));
        statusFilter = filter != null ? "1".equals(filter) : null;
    }
    
    /** @since 1.526 */
    public void setIncludeRegex(String includeRegex) {
        this.includeRegex = Util.nullify(includeRegex);
        if (this.includeRegex == null)
            this.includePattern = null;
        else
            this.includePattern = Pattern.compile(includeRegex);
    }

    @Extension @Symbol("list")
    public static class DescriptorImpl extends ViewDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ListView_DisplayName();
        }

        /**
         * Checks if the include regular expression is valid.
         */
        public FormValidation doCheckIncludeRegex( @QueryParameter String value ) throws IOException, ServletException, InterruptedException  {
            String v = Util.fixEmpty(value);
            if (v != null) {
                try {
                    Pattern.compile(v);
                } catch (PatternSyntaxException pse) {
                    return FormValidation.error(pse.getMessage());
                }
            }
            return FormValidation.ok();
        }
    }

    /**
     * @deprecated as of 1.391
     *  Use {@link ListViewColumn#createDefaultInitialColumnList()}
     */
    @Deprecated
    public static List<ListViewColumn> getDefaultColumns() {
        return ListViewColumn.createDefaultInitialColumnList(ListView.class);
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static final class Listener extends ItemListener {
        @Override
        public void onLocationChanged(final Item item, final String oldFullName, final String newFullName) {
            try (ACLContext acl = ACL.as(ACL.SYSTEM)) {
                locationChanged(oldFullName, newFullName);
            }
        }
        private void locationChanged(String oldFullName, String newFullName) {
            final Jenkins jenkins = Jenkins.getInstance();
            locationChanged(jenkins, oldFullName, newFullName);
            for (Item g : jenkins.allItems()) {
                if (g instanceof ViewGroup) {
                    locationChanged((ViewGroup) g, oldFullName, newFullName);
                }
            }
        }
        private void locationChanged(ViewGroup vg, String oldFullName, String newFullName) {
            for (View v : vg.getViews()) {
                if (v instanceof ListView) {
                    renameViewItem(oldFullName, newFullName, vg, (ListView) v);
                }
                if (v instanceof ViewGroup) {
                    locationChanged((ViewGroup) v, oldFullName, newFullName);
                }
            }
        }

        private void renameViewItem(String oldFullName, String newFullName, ViewGroup vg, ListView lv) {
            boolean needsSave;
            synchronized (lv) {
                Set<String> oldJobNames = new HashSet<String>(lv.jobNames);
                lv.jobNames.clear();
                for (String oldName : oldJobNames) {
                    lv.jobNames.add(Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName, oldName, vg.getItemGroup()));
                }
                needsSave = !oldJobNames.equals(lv.jobNames);
            }
            if (needsSave) { // do not hold ListView lock at the time
                try {
                    lv.save();
                } catch (IOException x) {
                    Logger.getLogger(ListView.class.getName()).log(Level.WARNING, null, x);
                }
            }
        }

        @Override
        public void onDeleted(final Item item) {
            try (ACLContext acl = ACL.as(ACL.SYSTEM)) {
                deleted(item);
            }
        }
        private void deleted(Item item) {
            final Jenkins jenkins = Jenkins.getInstance();
            deleted(jenkins, item);
            for (Item g : jenkins.allItems()) {
                if (g instanceof ViewGroup) {
                    deleted((ViewGroup) g, item);
                }
            }
        }
        private void deleted(ViewGroup vg, Item item) {
            for (View v : vg.getViews()) {
                if (v instanceof ListView) {
                    deleteViewItem(item, vg, (ListView) v);
                }
                if (v instanceof ViewGroup) {
                    deleted((ViewGroup) v, item);
                }
            }
        }

        private void deleteViewItem(Item item, ViewGroup vg, ListView lv) {
            boolean needsSave;
            synchronized (lv) {
                needsSave = lv.jobNames.remove(item.getRelativeNameFrom(vg.getItemGroup()));
            }
            if (needsSave) {
                try {
                    lv.save();
                } catch (IOException x) {
                    Logger.getLogger(ListView.class.getName()).log(Level.WARNING, null, x);
                }
            }
        }
    }

}
