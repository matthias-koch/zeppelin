/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.notebook.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NotebookRepoSyncTest {

  private File ZEPPELIN_HOME;
  private ZeppelinConfiguration conf;
  private File mainNotebookDir;
  private File secNotebookDir;
  private Notebook notebook;
  private NotebookRepoSync notebookRepoSync;
  private InterpreterFactory factory;
  private InterpreterSettingManager interpreterSettingManager;
  private Credentials credentials;
  private AuthenticationInfo anonymous;
  private NoteManager noteManager;
  private AuthorizationService authorizationService;
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoSyncTest.class);

  @BeforeEach
  public void setUp() throws Exception {
    System.setProperty("zeppelin.isTest", "true");
    ZEPPELIN_HOME = Files.createTempDir();
    new File(ZEPPELIN_HOME, "conf").mkdirs();
    String mainNotePath = ZEPPELIN_HOME.getAbsolutePath() + "/notebook";
    String secNotePath = ZEPPELIN_HOME.getAbsolutePath() + "/notebook_secondary";
    mainNotebookDir = new File(mainNotePath);
    secNotebookDir = new File(secNotePath);
    mainNotebookDir.mkdirs();
    secNotebookDir.mkdirs();

    System.setProperty(ConfVars.ZEPPELIN_HOME.getVarName(), ZEPPELIN_HOME.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), mainNotebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.VFSNotebookRepo,org.apache.zeppelin.notebook.repo.mock.VFSNotebookRepoMock");
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC.getVarName(), "false");
    System.setProperty(ConfVars.ZEPPELIN_CONFIG_FS_DIR.getVarName(), ZEPPELIN_HOME.getAbsolutePath() + "/conf");
    System.setProperty(ConfVars.ZEPPELIN_PLUGINS_DIR.getVarName(), new File("../../../plugins").getAbsolutePath());

    LOG.info("main Note dir : " + mainNotePath);
    LOG.info("secondary note dir : " + secNotePath);
    conf = ZeppelinConfiguration.create();

    ConfigStorage.reset();

    interpreterSettingManager = new InterpreterSettingManager(conf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class), mock(ApplicationEventListener.class));
    factory = new InterpreterFactory(interpreterSettingManager);

    notebookRepoSync = new NotebookRepoSync(conf);
    noteManager = new NoteManager(notebookRepoSync, conf);
    authorizationService = new AuthorizationService(noteManager, conf);
    credentials = new Credentials(conf);
    notebook = new Notebook(conf, authorizationService, notebookRepoSync, noteManager, factory, interpreterSettingManager, credentials, null);
    anonymous = new AuthenticationInfo("anonymous");
  }

  @AfterEach
  public void tearDown() throws Exception {
    delete(ZEPPELIN_HOME);
    System.clearProperty("zeppelin.isTest");
  }

  @Test
  void testRepoCount() throws IOException {
    assertTrue(notebookRepoSync.getMaxRepoNum() >= notebookRepoSync.getRepoCount());
  }

  @Test
  void testSyncOnCreate() throws IOException {
    /* check that both storage systems are empty */
    assertTrue(notebookRepoSync.getRepoCount() > 1);
    assertEquals(0, notebookRepoSync.list(0, anonymous).size());
    assertEquals(0, notebookRepoSync.list(1, anonymous).size());

    /* create note */
    notebook.createNote("test", "", anonymous);

    // check that automatically saved on both storages
    assertEquals(1, notebookRepoSync.list(0, anonymous).size());
    assertEquals(1, notebookRepoSync.list(1, anonymous).size());
    assertEquals(notebookRepoSync.list(0, anonymous).get(0).getId(), notebookRepoSync.list(1, anonymous).get(0).getId());

    NoteInfo noteInfo = notebookRepoSync.list(0, null).get(0);
    notebook.removeNote(noteInfo.getId(), anonymous);
  }

  @Test
  void testSyncOnDelete() throws IOException {
    /* create note */
    assertTrue(notebookRepoSync.getRepoCount() > 1);
    assertEquals(0, notebookRepoSync.list(0, anonymous).size());
    assertEquals(0, notebookRepoSync.list(1, anonymous).size());

    notebook.createNote("test", "", anonymous);

    /* check that created in both storage systems */
    assertEquals(1, notebookRepoSync.list(0, anonymous).size());
    assertEquals(1, notebookRepoSync.list(1, anonymous).size());
    assertEquals(notebookRepoSync.list(0, anonymous).get(0).getId(), notebookRepoSync.list(1, anonymous).get(0).getId());

    /* remove Note */
    NoteInfo noteInfo = notebookRepoSync.list(0, null).get(0);
    notebook.removeNote(noteInfo.getId(), anonymous);

    /* check that deleted in both storages */
    assertEquals(0, notebookRepoSync.list(0, anonymous).size());
    assertEquals(0, notebookRepoSync.list(1, anonymous).size());

  }

  @Test
  void testSyncUpdateMain() throws IOException {

    /* create note */
    String noteId = notebook.createNote("/test", "test", anonymous);
    notebook.processNote(noteId,
      note -> {
        note.setInterpreterFactory(mock(InterpreterFactory.class));
        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        Map<String, Object> config = p1.getConfig();
        config.put("enabled", true);
        p1.setConfig(config);
        p1.setText("hello world");

        /* new paragraph exists in note instance */
        assertEquals(1, note.getParagraphs().size());
        return null;
      });

    /* new paragraph not yet saved into storages */
    assertEquals(0, notebookRepoSync.get(0,
        notebookRepoSync.list(0, anonymous).get(0).getId(),
        notebookRepoSync.list(0, anonymous).get(0).getPath(), anonymous).getParagraphs().size());
    assertEquals(0, notebookRepoSync.get(1,
        notebookRepoSync.list(1, anonymous).get(0).getId(),
        notebookRepoSync.list(1, anonymous).get(0).getPath(), anonymous).getParagraphs().size());

    /* save to storage under index 0 (first storage) */
    notebook.processNote(noteId,
      note -> {
        notebookRepoSync.save(0, note, anonymous);
        return null;
      });

    /* check paragraph saved to first storage */
    assertEquals(1, notebookRepoSync.get(0,
        notebookRepoSync.list(0, anonymous).get(0).getId(),
        notebookRepoSync.list(0, anonymous).get(0).getPath(),
        anonymous).getParagraphs().size());
    /* check paragraph isn't saved to second storage */
    assertEquals(0, notebookRepoSync.get(1,
        notebookRepoSync.list(1, anonymous).get(0).getId(),
        notebookRepoSync.list(0, anonymous).get(0).getPath(),
        anonymous).getParagraphs().size());
    /* apply sync */
    notebookRepoSync.sync(null);
    /* check whether added to second storage */
    assertEquals(1, notebookRepoSync.get(1,
        notebookRepoSync.list(1, anonymous).get(0).getId(),
        notebookRepoSync.list(1, anonymous).get(0).getPath(), anonymous).getParagraphs().size());
    /* check whether same paragraph id */
    notebook.processNote(noteId,
      note -> {
        Paragraph p1 = note.getParagraph(0);
        assertEquals(p1.getId(), notebookRepoSync.get(0,
          notebookRepoSync.list(0, anonymous).get(0).getId(),
          notebookRepoSync.list(0, anonymous).get(0).getPath(), anonymous).getLastParagraph().getId());
        assertEquals(p1.getId(), notebookRepoSync.get(1,
            notebookRepoSync.list(1, anonymous).get(0).getId(),
            notebookRepoSync.list(1, anonymous).get(0).getPath(), anonymous).getLastParagraph().getId());
        notebookRepoSync.remove(note.getId(), note.getPath(), anonymous);
        return null;
      });

  }

  @Test
  void testSyncOnReloadedList() throws Exception {
    /* check that both storage repos are empty */
    assertTrue(notebookRepoSync.getRepoCount() > 1);
    assertEquals(0, notebookRepoSync.list(0, anonymous).size());
    assertEquals(0, notebookRepoSync.list(1, anonymous).size());

    File srcDir = new File("src/test/resources/notebook");
    File destDir = secNotebookDir;

    /* copy manually new notebook into secondary storage repo and check repos */
    try {
      FileUtils.copyDirectory(srcDir, destDir);
    } catch (IOException e) {
      LOG.error(e.toString(), e);
    }

    assertEquals(0, notebookRepoSync.list(0, anonymous).size());
    assertEquals(2, notebookRepoSync.list(1, anonymous).size());

    // After reloading notebooks repos should be synchronized
    notebook.reloadAllNotes(anonymous);
    assertEquals(2, notebookRepoSync.list(0, anonymous).size());
    assertEquals(2, notebookRepoSync.list(1, anonymous).size());
  }

  @Test
  void testOneWaySyncOnReloadedList() throws IOException, SchedulerException {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), mainNotebookDir.getAbsolutePath());
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC.getVarName(), "true");
    conf = ZeppelinConfiguration.create();
    notebookRepoSync = new NotebookRepoSync(conf);
    notebook = new Notebook(conf, mock(AuthorizationService.class), notebookRepoSync, new NoteManager(notebookRepoSync, conf), factory, interpreterSettingManager, credentials, null);

    // check that both storage repos are empty
    assertTrue(notebookRepoSync.getRepoCount() > 1);
    assertEquals(0, notebookRepoSync.list(0, null).size());
    assertEquals(0, notebookRepoSync.list(1, null).size());

    File srcDir = new File("src/test/resources/notebook");
    File destDir = secNotebookDir;

    /* copy manually new notebook into secondary storage repo and check repos */
    try {
      FileUtils.copyDirectory(srcDir, destDir);
    } catch (IOException e) {
      LOG.error(e.toString(), e);
    }
    assertEquals(0, notebookRepoSync.list(0, null).size());
    assertEquals(2, notebookRepoSync.list(1, null).size());

    // after reloading the notebook should be wiped from secondary storage
    notebook.reloadAllNotes(null);
    assertEquals(0, notebookRepoSync.list(0, null).size());
    assertEquals(0, notebookRepoSync.list(1, null).size());

    destDir = mainNotebookDir;

    // copy manually new notebook into primary storage repo and check repos
    try {
      FileUtils.copyDirectory(srcDir, destDir);
    } catch (IOException e) {
      LOG.error(e.toString(), e);
    }
    assertEquals(2, notebookRepoSync.list(0, null).size());
    assertEquals(0, notebookRepoSync.list(1, null).size());

    // after reloading notebooks repos should be synchronized
    notebook.reloadAllNotes(null);
    assertEquals(2, notebookRepoSync.list(0, null).size());
    assertEquals(2, notebookRepoSync.list(1, null).size());
  }

  @Test
  void testCheckpointOneStorage() throws IOException, SchedulerException {
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE.getVarName(), "org.apache.zeppelin.notebook.repo.GitNotebookRepo");
    ZeppelinConfiguration vConf = ZeppelinConfiguration.create();

    NotebookRepoSync vRepoSync = new NotebookRepoSync(vConf);
    Notebook vNotebookSync = new Notebook(vConf, mock(AuthorizationService.class), vRepoSync, new NoteManager(vRepoSync, conf), factory, interpreterSettingManager, credentials, null);

    // one git versioned storage initialized
    assertEquals(1, vRepoSync.getRepoCount());
    assertTrue(vRepoSync.getRepo(0) instanceof GitNotebookRepo);

    GitNotebookRepo gitRepo = (GitNotebookRepo) vRepoSync.getRepo(0);

    // no notes
    assertEquals(0, vRepoSync.list(anonymous).size());
    // create note
    String noteIdTmp = vNotebookSync.createNote("/test", "test", anonymous);
    System.out.println(noteIdTmp);
    Note note = vNotebookSync.processNote(noteIdTmp,
      noteTmp -> {
        return noteTmp;
    });
    assertEquals(1, vRepoSync.list(anonymous).size());
    System.out.println(note);

    NoteInfo noteInfo = vRepoSync.list(anonymous).values().iterator().next();
    String noteId = noteInfo.getId();
    String notePath = noteInfo.getPath();
    // first checkpoint
    vRepoSync.checkpoint(noteId, notePath, "checkpoint message", anonymous);
    int vCount = gitRepo.revisionHistory(noteId, notePath, anonymous).size();
    assertEquals(1, vCount);

    note.setInterpreterFactory(mock(InterpreterFactory.class));
    Paragraph p = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
    Map<String, Object> config = p.getConfig();
    config.put("enabled", true);
    p.setConfig(config);
    p.setText("%md checkpoint test");

    // save and checkpoint again
    vRepoSync.save(note, anonymous);
    vRepoSync.checkpoint(noteId, notePath, "checkpoint message 2", anonymous);
    assertEquals(vCount + 1, gitRepo.revisionHistory(noteId, notePath, anonymous).size());
    notebookRepoSync.remove(note.getId(), note.getPath(), anonymous);
  }

  @Test
  void testSyncWithAcl() throws IOException {
    /* scenario 1 - note exists with acl on main storage */
    AuthenticationInfo user1 = new AuthenticationInfo("user1");
    String noteId = notebook.createNote("/test", "test", user1);
    notebook.processNote(noteId,
      note -> {
        assertEquals(0, note.getParagraphs().size());
        return null;
      });

    // saved on both storages
    assertEquals(1, notebookRepoSync.list(0, null).size());
    assertEquals(1, notebookRepoSync.list(1, null).size());

    /* check that user1 is the only owner */
    Set<String> entity = new HashSet<String>();
    entity.add(user1.getUser());
    assertEquals(true, authorizationService.isOwner(noteId, entity));
    assertEquals(1, authorizationService.getOwners(noteId).size());
    assertEquals(0, authorizationService.getReaders(noteId).size());
    assertEquals(0, authorizationService.getRunners(noteId).size());
    assertEquals(0, authorizationService.getWriters(noteId).size());

    /* update note and save on secondary storage */
    notebook.processNote(noteId,
      note -> {
        note.setInterpreterFactory(mock(InterpreterFactory.class));
        Paragraph p1 = note.addNewParagraph(AuthenticationInfo.ANONYMOUS);
        p1.setText("hello world");
        assertEquals(1, note.getParagraphs().size());
        notebookRepoSync.save(1, note, null);
        return null;
      });


    /* check paragraph isn't saved into first storage */
    assertEquals(0, notebookRepoSync.get(0,
        notebookRepoSync.list(0, null).get(0).getId(),
        notebookRepoSync.list(0, null).get(0).getPath(), null).getParagraphs().size());
    /* check paragraph is saved into second storage */
    assertEquals(1, notebookRepoSync.get(1,
        notebookRepoSync.list(1, null).get(0).getId(),
        notebookRepoSync.list(1, null).get(0).getPath(), null).getParagraphs().size());

    /* now sync by user1 */
    notebookRepoSync.sync(user1);

    /* check that note updated and acl are same on main storage*/
    assertEquals(1, notebookRepoSync.get(0,
        notebookRepoSync.list(0, null).get(0).getId(),
        notebookRepoSync.list(0, null).get(0).getPath(), null).getParagraphs().size());
    assertEquals(true, authorizationService.isOwner(noteId, entity));
    assertEquals(1, authorizationService.getOwners(noteId).size());
    assertEquals(0, authorizationService.getReaders(noteId).size());
    assertEquals(0, authorizationService.getRunners(noteId).size());
    assertEquals(0, authorizationService.getWriters(noteId).size());

    /* scenario 2 - note doesn't exist on main storage */
    /* remove from main storage */
    notebook.processNote(noteId,
      note -> {
        notebookRepoSync.remove(0, noteId, note.getPath(), user1);
        return null;
      });
    assertEquals(0, notebookRepoSync.list(0, null).size());
    assertEquals(1, notebookRepoSync.list(1, null).size());

    /* now sync - should bring note from secondary storage with added acl */
    notebookRepoSync.sync(user1);
    assertEquals(1, notebookRepoSync.list(0, null).size());
    assertEquals(1, notebookRepoSync.list(1, null).size());
    assertEquals(1, authorizationService.getOwners(noteId).size());
    assertEquals(0, authorizationService.getReaders(noteId).size());
    assertEquals(0, authorizationService.getRunners(noteId).size());
    assertEquals(0, authorizationService.getWriters(noteId).size());
  }

  static void delete(File file) {
    if (file.isFile()) {
      file.delete();
    } else if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (File f : files) {
          delete(f);
        }
      }
      file.delete();
    }
  }
}
