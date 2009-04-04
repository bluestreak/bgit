package com.atlassian.labs.bamboo.git;

import com.atlassian.bamboo.commit.Commit;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.commit.CommitImpl;
import com.atlassian.bamboo.repository.*;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.security.EncryptionException;
import com.atlassian.bamboo.security.StringEncrypter;
import com.atlassian.bamboo.utils.ConfigUtils;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildChangesImpl;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.bamboo.author.AuthorImpl;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.pgm.Main;
import org.spearce.jgit.lib.*;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.transport.Transport;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class GitRepository extends AbstractRepository implements WebRepositoryEnabledRepository, InitialBuildAwareRepository, MutableQuietPeriodAwareRepository {
    
    private static final Log log = LogFactory.getLog(GitRepository.class);

    public static final String NAME = "Git";
    public static final String KEY = "git";

    public static final String REPO_PREFIX = "repository.git.";
    public static final String GIT_REPO_URL = REPO_PREFIX + "repositoryUrl";
    public static final String GIT_AUTH_TYPE = "repository.git.authType";
    public static final String GIT_USERNAME = REPO_PREFIX + "username";
    public static final String GIT_PASSWORD = REPO_PREFIX + "userPassword";
    public static final String GIT_PASSPHRASE = REPO_PREFIX + "passphrase";
    public static final String GIT_AUTHTYPE = REPO_PREFIX + "authType";
    public static final String GIT_KEYFILE = REPO_PREFIX + "keyFile";

    private static final String USE_EXTERNALS = REPO_PREFIX + "useExternals";
    private static final String EXTERNAL_PATH_MAPPINGS2 = REPO_PREFIX + "externalsToRevisionMappings";

    private static final String TEMPORARY_GIT_ADVANCED = "temporary.git.advanced";
    private static final String TEMPORARY_GIT_PASSWORD_CHANGE = "temporary.git.passwordChange";
    private static final String TEMPORARY_GIT_PASSPHRASE_CHANGE = "temporary.git.passphraseChange";
    private static final String TEMPORARY_GIT_PASSWORD = "temporary.git.password";
    private static final String TEMPORARY_GIT_PASSPHRASE = "temporary.git.passphrase";

    private String repositoryUrl;
    private String webRepositoryUrl;
    private String authType;
    private String username;
    private String password;
    private String passphrase;
    private String keyFile;
    private String webRepositoryUrlRepoName;

    private final QuietPeriodHelper quietPeriodHelper = new QuietPeriodHelper(REPO_PREFIX);
    private boolean quietPeriodEnabled = false;
    private int quietPeriod = QuietPeriodHelper.DEFAULT_QUIET_PERIOD;
    private int maxRetries = QuietPeriodHelper.DEFAULT_MAX_RETRIES;

    private Map<String, Long> externalPathRevisionMappings = new HashMap<String, Long>();

    private static final ThreadLocal<StringEncrypter> stringEncrypter = new ThreadLocal<StringEncrypter>() {
        protected StringEncrypter initialValue() {
            return new StringEncrypter();
        }
    };

    public void addDefaultValues(BuildConfiguration buildConfiguration) {
        super.addDefaultValues(buildConfiguration);
        quietPeriodHelper.addDefaultValues(buildConfiguration);
    }

    public synchronized BuildChanges collectChangesSinceLastBuild(String planKey, String lastVcsRevisionKey) throws RepositoryException {
        log.info("Determining if there have been changes since " + lastVcsRevisionKey);
        try {
            String repositoryUrl = getFullRepositoryUrl();
            File sourceDir = getSourceCodeDirectory(planKey);
            fetch(sourceDir, repositoryUrl);
            List<Commit> commits = new ArrayList<Commit>();
            String lastRevisionChecked = detectCommitsForUrl(repositoryUrl, lastVcsRevisionKey, commits, planKey);
            log.info("Doing build for latest changes till revision:" + lastRevisionChecked);
            return new BuildChangesImpl(lastRevisionChecked, commits);
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            throw new RepositoryException((new StringBuilder()).append("Build '").append(planKey).append("' failed to check Git repository").toString(), e);
        }
    }

    private void fetch(File sourceDir, String repositoryUrl) throws Exception {
        log.info("Fetching changes from remote repository");
        File gitDir = new File(sourceDir, ".git");

        // fetch the latest changes
        org.spearce.jgit.lib.Repository db = new org.spearce.jgit.lib.Repository(gitDir);
        Transport transport = Transport.open(db, "origin");
        try{
            transport.fetch(new TextProgressMonitor(),null);
        }finally{
            transport.close();
            if(db!=null){
                db.close();
            }
        }

    }

    private static void forceUpdate(org.spearce.jgit.lib.Repository db,final Ref branch) throws Exception {
        if (branch == null)
            throw new RepositoryException("Problem in updating the source code");

        final org.spearce.jgit.lib.Commit localCommit = db.mapCommit(Constants.HEAD);
        final org.spearce.jgit.lib.Commit remoteCommit = db.mapCommit(branch.getObjectId());

        log.info("Merging changes from :"+remoteCommit.getCommitId().name()+" to:"+localCommit.getCommitId().name());

        final RefUpdate u = db.updateRef(Constants.HEAD);
        u.setNewObjectId(remoteCommit.getCommitId());
        u.forceUpdate();

        final GitIndex index = new GitIndex(db);
        index.read();
        final WorkDirCheckout co = new WorkDirCheckout(db, db.getWorkDir(), localCommit.getTree(), index, remoteCommit.getTree());
        co.setFailOnConflict(false);
        co.checkout();
        index.write();
    }    

    private String detectCommitsForUrl(String repositoryUrl, final String lastRevisionChecked, final List<Commit> commits, String planKey) throws RepositoryException, IOException {
        log.info("Detecting commits after " + lastRevisionChecked);

        File gitDir = new File(getSourceCodeDirectory(planKey), ".git");
        org.spearce.jgit.lib.Repository db = new org.spearce.jgit.lib.Repository(gitDir);
        try{
            RevWalk walk = new RevWalk(db);

            final ObjectId fetchHead = db.getRef("refs/remotes/origin/master").getObjectId();
            walk.markStart(walk.parseCommit(fetchHead));

            for (final RevCommit revCommit : walk) {
                if(AnyObjectId.equals(revCommit.toObjectId(),ObjectId.fromString(lastRevisionChecked))){
                    break;
                }
                CommitImpl commit = new CommitImpl();
                commit.setAuthor(new AuthorImpl(revCommit.getAuthorIdent().getName()));
                commit.setDate(new Date(revCommit.getCommitTime()));
                commit.setComment(revCommit.getShortMessage());
                //TODO ADD LIST OF FILES FOR COMMIT
                commits.add(commit);
            }
            return fetchHead.name();
        }finally{
            if(db!=null){
                db.close();
            }
        }
    }


    public String retrieveSourceCode(String planKey, String vcsRevisionKey) throws RepositoryException {
        log.info("Retrieving the latest source code");
        try {
            String lastUpdated = retreiveSourceCodeWithException(planKey, vcsRevisionKey);
            log.info("Code updated till revision:"+lastUpdated);
            return lastUpdated;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RepositoryException("Problem in retrieving the latest source code.",e);
        }
    }

    private String retreiveSourceCodeWithException(String planKey, String vcsRevisionKey) throws Exception {
        String repositoryUrl = getFullRepositoryUrl();
        File sourceDir = getSourceCodeDirectory(planKey);
        File gitDir = new File(sourceDir, ".git");

        org.spearce.jgit.lib.Repository db = null;
        try{
            if (!sourceDir.exists() || !gitDir.exists()) {
                log.error("No Repository found, creating the repository");
                Main main = new Main();
                main.execute(new String[]{"clone", repositoryUrl , sourceDir.toString()});
                db = new org.spearce.jgit.lib.Repository(gitDir);
            } else {
                db = new org.spearce.jgit.lib.Repository(gitDir);
                forceUpdate(db,db.getRef("refs/remotes/origin/master"));
            }
            return db.getRef(Constants.HEAD).getObjectId().name();
        }finally {
            if(db!=null){
                db.close();
            }
        }
    }

    public ErrorCollection validate(BuildConfiguration buildConfiguration) {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        String repoUrl = buildConfiguration.getString(GIT_REPO_URL);
        if (StringUtils.isEmpty(repoUrl)) {
            errorCollection.addError(GIT_REPO_URL, "Please specify the build's Git Repository");
        } else {
            Matcher matcher = Pattern.compile("^([^:]+?):?(?::(\\d+))?((?:[A-Za-z]:)?/.+)$").matcher(repoUrl);
            if(!matcher.matches()){
                errorCollection.addError(GIT_REPO_URL, "Please enter valid repository URL. Format: host:port/git-repo Example: 10.0.0.128:22/git-repo/project (22 default SSH port)");
            }
        }

        String authenticationType = buildConfiguration.getString(GIT_AUTH_TYPE);
        if(authenticationType.equals(AuthenticationType.SSH.getKey())){
            errorCollection.addError(GIT_AUTH_TYPE, "SSH Authentication is not yet supported with GIT repository.");
        }

        String webRepoUrl = buildConfiguration.getString(WEB_REPO_URL);
//        if (!StringUtils.isEmpty(webRepoUrl) && !UrlUtils.verifyHierachicalURI(webRepoUrl)) {
//            errorCollection.addError(WEB_REPO_URL, "This is not a valid url");
//        }

        quietPeriodHelper.validate(buildConfiguration, errorCollection);
        log.debug("Validation results:" + errorCollection);
        return errorCollection;
    }


    public boolean isRepositoryDifferent(Repository repository) {
        if (repository != null && repository instanceof GitRepository) {
            GitRepository gitRepository = (GitRepository) repository;
            return !new EqualsBuilder()
                    .append(this.getName(), gitRepository.getName())
                    .append(getRepositoryUrl(), gitRepository.getRepositoryUrl())
                    .isEquals();
        } else {
            return true;
        }
    }

    public void prepareConfigObject(BuildConfiguration buildConfiguration) {
        String repositoryKey = buildConfiguration.getString(SELECTED_REPOSITORY);
        String authType = buildConfiguration.getString(GIT_AUTH_TYPE);
        
        if (AuthenticationType.PASSWORD.getKey().equals(authType)) {
            boolean svnPasswordChanged = buildConfiguration.getBoolean(TEMPORARY_GIT_PASSWORD_CHANGE);
            if (svnPasswordChanged) {
                String newPassword = buildConfiguration.getString(TEMPORARY_GIT_PASSWORD);
                if (getKey().equals(repositoryKey)) {
                    buildConfiguration.setProperty(GitRepository.GIT_PASSWORD, stringEncrypter.get().encrypt(newPassword));
                }
            }
        } else {
            boolean passphraseChanged = buildConfiguration.getBoolean(TEMPORARY_GIT_PASSPHRASE_CHANGE);
            if (passphraseChanged) {
                String newPassphrase = buildConfiguration.getString(TEMPORARY_GIT_PASSPHRASE);
                buildConfiguration.setProperty(GitRepository.GIT_PASSPHRASE, stringEncrypter.get().encrypt(newPassphrase));
            }
        }

        // Disabling advanced will clear all advanced
        if (!buildConfiguration.getBoolean(TEMPORARY_GIT_ADVANCED, false)) {
            quietPeriodHelper.clearFromBuildConfiguration(buildConfiguration);
            buildConfiguration.clearTree(USE_EXTERNALS);
        }
    }

    public void populateFromConfig(HierarchicalConfiguration config) {
        super.populateFromConfig(config);

        setRepositoryUrl(config.getString(GIT_REPO_URL));
        setUsername(config.getString(GIT_USERNAME));
        setAuthType(config.getString(GIT_AUTHTYPE));
        if (AuthenticationType.SSH.getKey().equals(authType)) {
            setEncryptedPassphrase(config.getString(GIT_PASSPHRASE));
            setKeyFile(config.getString(GIT_KEYFILE));
        } else {
            setEncryptedPassword(config.getString(GIT_PASSWORD));
        }
        setWebRepositoryUrl(config.getString(WEB_REPO_URL));
        setWebRepositoryUrlRepoName(config.getString(WEB_REPO_MODULE_NAME));

        final Map<String, String> stringMaps = ConfigUtils.getMapFromConfiguration(EXTERNAL_PATH_MAPPINGS2, config);
        externalPathRevisionMappings = ConfigUtils.toLongMap(stringMaps);

        quietPeriodHelper.populateFromConfig(config, this);
    }


    public HierarchicalConfiguration toConfiguration() {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(GIT_REPO_URL, getRepositoryUrl());
        configuration.setProperty(GIT_USERNAME, getUsername());
        configuration.setProperty(GIT_AUTHTYPE, getAuthType());
        if (AuthenticationType.SSH.getKey().equals(authType)) {
            configuration.setProperty(GIT_PASSPHRASE, getEncryptedPassphrase());
            configuration.setProperty(GIT_KEYFILE, getKeyFile());
        } else {
            configuration.setProperty(GIT_PASSWORD, getEncryptedPassword());
        }
        configuration.setProperty(WEB_REPO_URL, getWebRepositoryUrl());
        configuration.setProperty(WEB_REPO_MODULE_NAME, getWebRepositoryUrlRepoName());

        final Map<String, String> stringMap = ConfigUtils.toStringMap(externalPathRevisionMappings);
        ConfigUtils.addMapToBuilConfiguration(EXTERNAL_PATH_MAPPINGS2, stringMap, configuration);

        quietPeriodHelper.toConfiguration(configuration, this);
        return configuration;
    }

    public void onInitialBuild(BuildContext buildContext) {
    }

    public boolean isAdvancedOptionEnabled(BuildConfiguration buildConfiguration) {
        final boolean useExternals = buildConfiguration.getBoolean(USE_EXTERNALS, false);
        final boolean quietPeriodEnabled = quietPeriodHelper.isEnabled(buildConfiguration);
        return useExternals || quietPeriodEnabled;
    }

    public String getName() {
        return NAME;
    }

    public String getPassphrase() {
        try {
            return new StringEncrypter().decrypt(passphrase);
        }
        catch (Exception e) {
            log.error(e);            
        }
        return null;
    }

    public void setPassphrase(String passphrase) {
        try {
            if (StringUtils.isNotEmpty(passphrase)) {
                this.passphrase = new StringEncrypter().encrypt(passphrase);
            } else {
                this.passphrase = passphrase;
            }
        }
        catch (EncryptionException e) {
            log.error("Failed to encrypt password", e);
            this.passphrase = null;
        }
    }

    public String getEncryptedPassphrase() {
        return passphrase;
    }

    public void setEncryptedPassphrase(String encryptedPassphrase) {
        passphrase = encryptedPassphrase;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(String myKeyFile) {
        this.keyFile = myKeyFile;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    // Where is the documentation and help about using Git?
    public String getUrl() {
        return "http://git-scm.com/";
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = StringUtils.trim(repositoryUrl);
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getFullRepositoryUrl() {
        if(getUsername()!=null){
            return "ssh://"+getUsername()+":"+(getUserPassword()!=null?getUserPassword():"")+"@"+repositoryUrl;
        }else{
            return "ssh://"+repositoryUrl;
        }
    }

    public void setUsername(String username) {
        this.username = StringUtils.trim(username);
    }

    public String getUsername() {
        return username;
    }

    public void setUserPassword(String password) {
        try {
            if (StringUtils.isNotEmpty(password)) {
                this.password = new StringEncrypter().encrypt(password);
            } else {
                this.password = password;
            }
        }
        catch (EncryptionException e) {
            log.error("Failed to encrypt password", e);
            this.password = null;
        }
    }

    public String getUserPassword() {
        try {
            return new StringEncrypter().decrypt(password);
        }
        catch (Exception e) {
            return null;
        }
    }

    public String getEncryptedPassword() {
        return password;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        password = encryptedPassword;
    }

    public boolean hasWebBasedRepositoryAccess() {
        return StringUtils.isNotBlank(webRepositoryUrl);
    }

    public String getWebRepositoryUrl() {
        return webRepositoryUrl;
    }

    public void setWebRepositoryUrl(String url) {
        webRepositoryUrl = StringUtils.trim(url);
    }

    public String getWebRepositoryUrlRepoName() {
        return webRepositoryUrlRepoName;
    }

    public void setWebRepositoryUrlRepoName(String repoName) {
        webRepositoryUrlRepoName = StringUtils.trim(repoName);
    }

    public String getWebRepositoryUrlForFile(CommitFile file) {
//        ViewCvsFileLinkGenerator fileLinkGenerator = new ViewCvsFileLinkGenerator(webRepositoryUrl);
        return null;//fileLinkGenerator.getWebRepositoryUrlForFile(file, webRepositoryUrlRepoName, ViewCvsFileLinkGenerator.GIT_REPO_TYPE);
    }

    public String getWebRepositoryUrlForDiff(CommitFile file) {
//        ViewCvsFileLinkGenerator fileLinkGenerator = new ViewCvsFileLinkGenerator(webRepositoryUrl);
        return null;// fileLinkGenerator.getWebRepositoryUrlForDiff(file, webRepositoryUrlRepoName, ViewCvsFileLinkGenerator.GIT_REPO_TYPE);
    }

    public String getWebRepositoryUrlForRevision(CommitFile file) {
//        ViewCvsFileLinkGenerator fileLinkGenerator = new ViewCvsFileLinkGenerator(webRepositoryUrl);
        return null;// fileLinkGenerator.getWebRepositoryUrlForRevision(file, webRepositoryUrlRepoName, ViewCvsFileLinkGenerator.GIT_REPO_TYPE);
    }

    @Override
    public String getWebRepositoryUrlForCommit(Commit commit) {
//        ViewCvsFileLinkGenerator fileLinkGenerator = new ViewCvsFileLinkGenerator(webRepositoryUrl);
        return null;// fileLinkGenerator.getWebRepositoryUrlForCommit(commit, webRepositoryUrlRepoName, ViewCvsFileLinkGenerator.GIT_REPO_TYPE);
    }

    public String getHost() {
        if (repositoryUrl == null) {
            return UNKNOWN_HOST;
        }
        try {
            URL url = new URL(getFullRepositoryUrl());
            return url.getHost();
        } catch (MalformedURLException e) {
            return UNKNOWN_HOST;
        }
    }

    public boolean isQuietPeriodEnabled() {
        return quietPeriodEnabled;
    }

    public void setQuietPeriodEnabled(boolean quietPeriodEnabled) {
        this.quietPeriodEnabled = quietPeriodEnabled;
    }

    public int getQuietPeriod() {
        return quietPeriod;
    }

    public void setQuietPeriod(int quietPeriod) {
        this.quietPeriod = quietPeriod;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int hashCode() {
        return new HashCodeBuilder(101, 11)
                .append(getKey())
                .append(getRepositoryUrl())
                .append(getUsername())
                .append(getEncryptedPassword())
                .append(getWebRepositoryUrl())
                .append(getWebRepositoryUrlRepoName())
                .append(getTriggerIpAddress())
                .toHashCode();
    }

    public boolean equals(Object o) {
        if (!(o instanceof GitRepository)) {
            return false;
        }
        GitRepository rhs = (GitRepository) o;
        return new EqualsBuilder()
                .append(getRepositoryUrl(), rhs.getRepositoryUrl())
                .append(getUsername(), rhs.getUsername())
                .append(getEncryptedPassword(), rhs.getEncryptedPassword())
                .append(getWebRepositoryUrl(), rhs.getWebRepositoryUrl())
                .append(getWebRepositoryUrlRepoName(), rhs.getWebRepositoryUrlRepoName())
                .append(getTriggerIpAddress(), rhs.getTriggerIpAddress())
                .isEquals();
    }

    public int compareTo(Object obj) {
        GitRepository o = (GitRepository) obj;
        return new CompareToBuilder()
                .append(getRepositoryUrl(), o.getRepositoryUrl())
                .append(getUsername(), o.getUsername())
                .append(getEncryptedPassword(), o.getEncryptedPassword())
                .append(getWebRepositoryUrl(), o.getWebRepositoryUrl())
                .append(getWebRepositoryUrlRepoName(), o.getWebRepositoryUrlRepoName())
                .append(getTriggerIpAddress(), o.getTriggerIpAddress())
                .toComparison();
    }

    public List getAuthenticationTypes() {
        List<NameValuePair> types = new ArrayList<NameValuePair>();
        types.add(AuthenticationType.PASSWORD.getNameValue());
        types.add(AuthenticationType.SSH.getNameValue());
        return types;
    }


}