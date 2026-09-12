-- NAGEEB STUDY OS: Room schema version 1
-- Generated from the exported Room schema, with application validation triggers.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS `Subject` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `accent` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_Subject_position` ON `Subject` (`position`);
CREATE INDEX IF NOT EXISTS `index_Subject_updatedAt` ON `Subject` (`updatedAt`);

CREATE TABLE IF NOT EXISTS `Folder` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `parentId` TEXT, `title` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`subjectId`) REFERENCES `Subject`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`subjectId`, `parentId`) REFERENCES `Folder`(`subjectId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED);
CREATE UNIQUE INDEX IF NOT EXISTS `index_Folder_subjectId_id` ON `Folder` (`subjectId`, `id`);
CREATE INDEX IF NOT EXISTS `index_Folder_subjectId_parentId` ON `Folder` (`subjectId`, `parentId`);
CREATE INDEX IF NOT EXISTS `index_Folder_parentId` ON `Folder` (`parentId`);
CREATE INDEX IF NOT EXISTS `index_Folder_position` ON `Folder` (`position`);

CREATE TABLE IF NOT EXISTS `Lesson` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `folderId` TEXT, `title` TEXT NOT NULL, `position` INTEGER NOT NULL, `status` TEXT NOT NULL, `understanding` INTEGER NOT NULL, `application` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`subjectId`) REFERENCES `Subject`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`subjectId`, `folderId`) REFERENCES `Folder`(`subjectId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED);
CREATE INDEX IF NOT EXISTS `index_Lesson_subjectId_folderId` ON `Lesson` (`subjectId`, `folderId`);
CREATE INDEX IF NOT EXISTS `index_Lesson_folderId` ON `Lesson` (`folderId`);
CREATE INDEX IF NOT EXISTS `index_Lesson_updatedAt` ON `Lesson` (`updatedAt`);
CREATE INDEX IF NOT EXISTS `index_Lesson_position` ON `Lesson` (`position`);

CREATE TABLE IF NOT EXISTS `StudyFile` (`id` TEXT NOT NULL, `lessonId` TEXT NOT NULL, `title` TEXT NOT NULL, `location` TEXT NOT NULL, `mime` TEXT NOT NULL, `size` INTEGER NOT NULL, `storageType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`lessonId`) REFERENCES `Lesson`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_StudyFile_lessonId` ON `StudyFile` (`lessonId`);
CREATE INDEX IF NOT EXISTS `index_StudyFile_updatedAt` ON `StudyFile` (`updatedAt`);

CREATE TABLE IF NOT EXISTS `StudyLink` (`id` TEXT NOT NULL, `lessonId` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`lessonId`) REFERENCES `Lesson`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_StudyLink_lessonId` ON `StudyLink` (`lessonId`);

CREATE TABLE IF NOT EXISTS `Note` (`id` TEXT NOT NULL, `subjectId` TEXT, `folderId` TEXT, `lessonId` TEXT, `fileId` TEXT, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`subjectId`) REFERENCES `Subject`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`folderId`) REFERENCES `Folder`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`lessonId`) REFERENCES `Lesson`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`fileId`) REFERENCES `StudyFile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_Note_subjectId` ON `Note` (`subjectId`);
CREATE INDEX IF NOT EXISTS `index_Note_folderId` ON `Note` (`folderId`);
CREATE INDEX IF NOT EXISTS `index_Note_lessonId` ON `Note` (`lessonId`);
CREATE INDEX IF NOT EXISTS `index_Note_fileId` ON `Note` (`fileId`);
CREATE INDEX IF NOT EXISTS `index_Note_updatedAt` ON `Note` (`updatedAt`);

CREATE TABLE IF NOT EXISTS `Tag` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `normalized` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`));
CREATE UNIQUE INDEX IF NOT EXISTS `index_Tag_normalized` ON `Tag` (`normalized`);

CREATE TABLE IF NOT EXISTS `LessonTag` (`id` TEXT NOT NULL, `lessonId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`lessonId`) REFERENCES `Lesson`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `Tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE UNIQUE INDEX IF NOT EXISTS `index_LessonTag_lessonId_tagId` ON `LessonTag` (`lessonId`, `tagId`);
CREATE INDEX IF NOT EXISTS `index_LessonTag_tagId` ON `LessonTag` (`tagId`);

CREATE TABLE IF NOT EXISTS `FileTag` (`id` TEXT NOT NULL, `fileId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`fileId`) REFERENCES `StudyFile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `Tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE UNIQUE INDEX IF NOT EXISTS `index_FileTag_fileId_tagId` ON `FileTag` (`fileId`, `tagId`);
CREATE INDEX IF NOT EXISTS `index_FileTag_tagId` ON `FileTag` (`tagId`);

CREATE TABLE IF NOT EXISTS `NoteTag` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`noteId`) REFERENCES `Note`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `Tag`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE UNIQUE INDEX IF NOT EXISTS `index_NoteTag_noteId_tagId` ON `NoteTag` (`noteId`, `tagId`);
CREATE INDEX IF NOT EXISTS `index_NoteTag_tagId` ON `NoteTag` (`tagId`);

CREATE TABLE IF NOT EXISTS `AppSetting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`));

CREATE TABLE IF NOT EXISTS `PendingDeletion` (`id` TEXT NOT NULL, `path` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE TRIGGER note_owner_insert BEFORE INSERT ON Note WHEN ((NEW.subjectId IS NOT NULL) + (NEW.folderId IS NOT NULL) + (NEW.lessonId IS NOT NULL) + (NEW.fileId IS NOT NULL)) != 1 BEGIN SELECT RAISE(ABORT, 'invalid note owner'); END;
CREATE TRIGGER lesson_progress_insert BEFORE INSERT ON Lesson WHEN NEW.understanding NOT BETWEEN 0 AND 100 OR NEW.application NOT BETWEEN 0 AND 100 OR NEW.revision NOT BETWEEN 0 AND 100 OR NEW.status NOT IN ('NOT_STARTED','IN_PROGRESS','NEEDS_REVIEW','MASTERED','WEAK') BEGIN SELECT RAISE(ABORT, 'invalid lesson progress'); END;
CREATE TRIGGER file_storage_insert BEFORE INSERT ON StudyFile WHEN NEW.storageType NOT IN ('LINKED','IMPORTED') BEGIN SELECT RAISE(ABORT, 'invalid storage type'); END;
CREATE TRIGGER note_owner_update BEFORE UPDATE ON Note WHEN ((NEW.subjectId IS NOT NULL) + (NEW.folderId IS NOT NULL) + (NEW.lessonId IS NOT NULL) + (NEW.fileId IS NOT NULL)) != 1 BEGIN SELECT RAISE(ABORT, 'invalid note owner'); END;
CREATE TRIGGER lesson_progress_update BEFORE UPDATE ON Lesson WHEN NEW.understanding NOT BETWEEN 0 AND 100 OR NEW.application NOT BETWEEN 0 AND 100 OR NEW.revision NOT BETWEEN 0 AND 100 OR NEW.status NOT IN ('NOT_STARTED','IN_PROGRESS','NEEDS_REVIEW','MASTERED','WEAK') BEGIN SELECT RAISE(ABORT, 'invalid lesson progress'); END;
CREATE TRIGGER file_storage_update BEFORE UPDATE ON StudyFile WHEN NEW.storageType NOT IN ('LINKED','IMPORTED') BEGIN SELECT RAISE(ABORT, 'invalid storage type'); END;
