export default {
  common: {
    cancel: 'Cancel',
    delete: 'Delete',
    save: 'Save',
    confirm: 'Confirm',
    loading: 'Loading...',
    failed: 'Operation failed',
    success: 'Operation successful',
    add: 'Add',
    create: 'Create',
    rename: 'Rename',
    edit: 'Edit',
    search: 'Search',
    noData: 'No data',
    actions: 'Actions',
    previous: 'Previous',
    next: 'Next'
  },
  auth: {
    login: {
      subtitle: 'Sign in to your navigation bar',
      username: 'Username',
      password: 'Password',
      signIn: 'Sign In',
      noAccount: "Don't have an account?",
      createOne: 'Create one',
      validation: {
        usernameRequired: 'Please enter your username',
        usernameLength: 'Username must be 3-50 characters',
        passwordRequired: 'Please enter your password',
        passwordLength: 'Password must be at least 6 characters'
      },
      toast: {
        success: 'Login successful',
        signedOut: 'Signed out',
        signOutFailed: 'Failed to sign out'
      }
    },
    register: {
      subtitle: 'Create your account',
      username: 'Username',
      password: 'Password',
      confirmPassword: 'Confirm password',
      nickname: 'Nickname (optional)',
      nicknamePlaceholder: 'Give yourself a name',
      createAccount: 'Create Account',
      hasAccount: 'Already have an account?',
      signIn: 'Sign in',
      validation: {
        usernameRequired: 'Please enter a username',
        usernameLength: 'Username must be 3-50 characters',
        passwordRequired: 'Please enter a password',
        passwordLength: 'Password must be at least 6 characters',
        confirmPasswordRequired: 'Please confirm your password',
        passwordMismatch: 'Passwords do not match',
        nicknameLength: 'Nickname must be no more than 15 characters'
      },
      toast: {
        success: 'Registration successful'
      }
    }
  },
  nav: {
    bookmarks: 'Bookmarks',
    staging: 'Staging',
    admin: 'Admin',
    settings: 'Settings',
    signOut: 'Sign Out'
  },
  bookmarks: {
    loading: 'Loading bookmarks...',
    addBookmark: 'Add Bookmark',
    selectFolder: 'Select a folder',
    empty: {
      noBookmarks: 'No bookmarks yet',
      addFirst: 'Add your first bookmark to this folder',
      chooseFolder: 'Choose a folder from the sidebar to view its bookmarks'
    },
    contextMenu: {
      edit: 'Edit',
      delete: 'Delete',
      moveTo: 'Move to...',
      moveSelectedTo: 'Move selected to...'
    },
    editDialog: {
      title: 'Edit Bookmark'
    },
    form: {
      title: 'Title',
      url: 'URL',
      iconUrl: 'Icon URL (optional)',
      titlePlaceholder: 'Bookmark title',
      urlPlaceholder: 'https://example.com',
      iconUrlPlaceholder: 'Favicon URL'
    },
    validation: {
      titleRequired: 'Please enter a title',
      urlRequired: 'Please enter a URL'
    },
    toast: {
      added: 'Bookmark added',
      updated: 'Bookmark updated',
      deleted: 'Bookmark deleted',
      orderFailed: 'Failed to update bookmark order',
      moved: 'Bookmark moved',
      movedBatch: 'Moved {count} bookmark(s)',
      moveFailed: 'Failed to move bookmark(s)'
    },
    deleteConfirm: {
      title: 'Delete "{title}"?',
      confirmTitle: 'Delete Bookmark'
    },
    batch: {
      confirmTitle: 'Delete Bookmarks',
      confirmMessage: 'Delete {count} selected bookmarks?',
      deleted: 'Deleted {count} bookmark(s)',
      linksCopied: 'Links copied to clipboard',
      copyFailed: 'Failed to copy links'
    },
    moveDialog: {
      title: 'Move to Folder',
      description: 'Select a target folder:',
      confirm: 'Move',
      empty: 'No folders available'
    },
    importDialog: {
      title: 'Import Bookmarks',
      selectFile: 'Select File',
      fileTip: 'Only Chrome bookmark HTML files are supported',
      targetFolder: 'Target Folder',
      targetFolderHint: 'Bookmarks will be imported under the selected folder',
      duplicateMode: 'Duplicate Handling',
      overwrite: 'Overwrite',
      skip: 'Skip',
      importBtn: 'Import',
      importing: 'Importing...',
      noFile: 'Please select a file to import first',
      result: 'Import complete: {folders} folder(s), {bookmarks} bookmark(s), {duplicates} {mode}',
      overwritten: 'overwritten',
      skipped: 'skipped',
      failed: 'Import failed. Please check the file format.'
    }
  },
  batchToolbar: {
    selected: 'selected',
    delete: 'Delete',
    copyLinks: 'Copy Links',
    selectAll: 'Select All',
    deselectAll: 'Deselect All'
  },
  folders: {
    newFolder: 'New Folder',
    noFolders: 'No folders yet',
    renameFolder: 'Rename Folder',
    folderName: 'Folder name',
    create: 'Create',
    save: 'Save',
    contextMenu: {
      rename: 'Rename',
      newSubfolder: 'New Subfolder',
      delete: 'Delete'
    },
    validation: {
      nameRequired: 'Please enter a folder name'
    },
    toast: {
      created: 'Folder created',
      renamed: 'Folder renamed',
      deleted: 'Folder deleted',
      orderFailed: 'Failed to update folder order'
    },
    confirm: {
      title: 'Delete Folder',
      message: 'This folder and all its subfolders and bookmarks will be permanently deleted. This action cannot be undone.'
    },
    newFolderDialog: {
      title: 'New Folder'
    }
  },
  staging: {
    empty: {
      title: 'Staging area is empty',
      description: 'Save links here to read or organize later'
    },
    timer: {
      noExpiry: 'No expiry',
      expired: 'Expired'
    },
    tooltips: {
      moveToFolder: 'Move to Folder',
      setExpiry: 'Set Expiry',
      delete: 'Delete'
    },
    view: {
      title: 'Staging Area',
      addToStaging: 'Add to Staging',
      toast: {
        signedOut: 'Signed out',
        signOutFailed: 'Failed to sign out',
        loadFailed: 'Failed to load staging data',
        added: 'Added to staging',
        moved: 'Moved to folder',
        expiryUpdated: 'Expiry time updated',
        removed: 'Removed from staging'
      },
      addDialog: {
        title: 'Add to Staging',
        titlePlaceholder: 'Page title',
        urlPlaceholder: 'https://example.com',
        iconUrlPlaceholder: 'Favicon URL'
      },
      moveDialog: {
        title: 'Move to Folder',
        description: 'Select a folder to move this item to:',
        empty: 'No folders available. Create one in the Bookmarks page first.'
      },
      expiryDialog: {
        title: 'Set Expiry Time',
        label: 'Expire after (minutes)',
        presets: {
          '30min': '30 min',
          '1hour': '1 hour',
          '6hours': '6 hours',
          '1day': '1 day',
          '3days': '3 days',
          '7days': '7 days'
        }
      },
      removeConfirm: {
        title: 'Remove from Staging',
        message: 'Are you sure you want to remove this item from staging?'
      },
      validation: {
        titleRequired: 'Please enter a title',
        urlRequired: 'Please enter a URL'
      }
    }
  },
  admin: {
    users: {
      title: 'User Management',
      countSuffix: 'users',
      loading: 'Loading users...',
      tableHeaders: {
        username: 'Username',
        email: 'Email',
        role: 'Role',
        status: 'Status',
        joined: 'Joined',
        actions: 'Actions'
      },
      status: {
        active: 'Active',
        banned: 'Banned'
      },
      tooltips: {
        viewDetails: 'View Details',
        unban: 'Unban User',
        ban: 'Ban User'
      },
      empty: {
        noUsers: 'No users found',
        description: 'There are no registered users in the system'
      },
      toast: {
        loadFailed: 'Failed to load users',
        unbanned: 'User {username} has been unbanned',
        banned: 'User {username} has been banned',
        actionFailed: 'Failed to ban/unban user'
      },
      banConfirm: {
        title: 'Ban User',
        message: 'Ban user {username}? They will not be able to log in.'
      }
    },
    userDetail: {
      backToUsers: 'Back to Users',
      loading: 'Loading user data...',
      noEmail: 'No email',
      labels: {
        role: 'Role',
        status: 'Status',
        joined: 'Joined',
        active: 'Active',
        banned: 'Banned'
      },
      sidebar: {
        folders: 'Folders',
        noFolders: 'No folders'
      },
      empty: {
        noFolders: 'This user has not created any folders yet.'
      },
      content: {
        selectFolder: 'Select a folder',
        noBookmarks: 'No bookmarks in this folder',
        chooseFolder: 'Select a folder to view bookmarks'
      },
      deleteConfirm: {
        title: 'Delete Folder',
        message: '{name} and all its contents will be permanently deleted. This action cannot be undone.'
      },
      error: {
        notFound: 'User not found',
        notFoundDescription: 'The requested user does not exist or has been removed.'
      },
      toast: {
        loadFailed: 'Failed to load user data',
        bookmarksLoadFailed: 'Failed to load bookmarks',
        folderDeleted: 'Folder deleted'
      }
    }
  },
  mainView: {
    toast: {
      foldersLoadFailed: 'Failed to load folders'
    }
  },
  notFound: {
    title: 'Page Not Found',
    description: 'The page you are looking for does not exist',
    goBack: 'Back to Home'
  },
  settings: {
    title: 'Settings',
    profile: {
      title: 'Profile',
      nickname: 'Nickname',
      nicknamePlaceholder: 'Enter nickname',
      email: 'Email',
      emailPlaceholder: 'Enter email address',
      save: 'Save Changes',
      toast: {
        saved: 'Profile updated',
        loadFailed: 'Failed to load profile',
        saveFailed: 'Failed to save'
      }
    },
    password: {
      title: 'Change Password',
      oldPassword: 'Current Password',
      oldPasswordPlaceholder: 'Enter current password',
      newPassword: 'New Password',
      newPasswordPlaceholder: 'Enter new password (min 6 characters)',
      confirmPassword: 'Confirm New Password',
      confirmPasswordPlaceholder: 'Re-enter new password',
      change: 'Change Password',
      toast: {
        changed: 'Password changed successfully',
        mismatch: 'New passwords do not match',
        wrongOld: 'Current password is incorrect'
      },
      validation: {
        oldRequired: 'Please enter current password',
        newRequired: 'Please enter new password',
        newLength: 'New password must be at least 6 characters',
        confirmRequired: 'Please confirm new password'
      }
    }
  }
}
