# Makefile for module 'bcutil'
# (c) Copyright IBM Corp. 1998, 2001 All Rights Reserved
# Timestamp: 1.5, 10/2/01
#
# Autogenerated Code

CPU=ARM

maj_ver=2
min_ver=017
bld_num=0

DLLPREFIX=swt
OSPREFIX=win32-ce
DLLNAME=$(DLLPREFIX)-$(OSPREFIX)-$(maj_ver)$(min_ver).dll

LIBPATH=# declaration
LIBNAME=$(DLLPREFIX)-$(OSPREFIX)-$(maj_ver)$(min_ver).lib

SWTDEFS=-DSWT_LIBRARY_VERSION=$(maj_ver)$(min_ver) -DSWT_LIBRARY_BUILD_NUM=$(bld_num)
.c.obj:
	clarm /nologo /c /W3 $(SWTDEFS) -DJ9WINCE -D _WIN32_WCE=300 -D "MS Pocket PC" /D UNDER_CE=300 /D "UNICODE" /D "_MBCS" /Zm200 -DARM -D_ARM_ -DFIXUP_UNALIGNED /I. /I$(JAVA_HOME)\include $*.c

.rc.res:
	rc $<

BUILDFILES1 = swt.obj structs.obj callback.obj globals.obj library.obj

VIRTFILES1 = # swt.res

all: \
	 $(LIBNAME) $(DLLNAME)

BUILDLIB: $(LIBPATH)$(LIBNAME)

$(LIBPATH)$(LIBNAME):\
	$(BUILDFILES1) $(VIRTFILES1)
	lib -subsystem:windowsce,3.00 -out:$(LIBPATH)$(LIBNAME).lib /NODEFAULTLIB:libc.lib /nodefaultlib:oldnames.lib -machine:$(CPU) \
	$(BUILDFILES1) $(VIRTFILES1) 

$(DLLNAME): $(LIBPATH)$(LIBNAME) \
	$(BUILDFILES1) $(VIRTFILES1)
	link $(dlllflags) -machine:$(CPU) \
	-subsystem:windowsce,3.00 -out:$(DLLNAME) -map:$(LIBNAME).map \
	$(BUILDFILES1) $(VIRTFILES1) \
	/dll  /entry:"_DllMainCRTStartup" /NODEFAULTLIB:libc.lib /nodefaultlib:oldnames.lib aygshell.lib corelibc.lib coredll.lib commdlg.lib commctrl.lib ceshell.lib
