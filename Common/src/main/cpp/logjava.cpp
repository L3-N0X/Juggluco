/*      This file is part of Juggluco, an Android app to receive and display         */
/*      glucose values from Freestyle Libre 2 and 3 sensors.                         */
/*                                                                                   */
/*      Copyright (C) 2021 Jaap Korthals Altes <jaapkorthalsaltes@gmail.com>         */
/*                                                                                   */
/*      Juggluco is free software: you can redistribute it and/or modify             */
/*      it under the terms of the GNU General Public License as published            */
/*      by the Free Software Foundation, either version 3 of the License, or         */
/*      (at your option) any later version.                                          */
/*                                                                                   */
/*      Juggluco is distributed in the hope that it will be useful, but              */
/*      WITHOUT ANY WARRANTY; without even the implied warranty of                   */
/*      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         */
/*      See the GNU General Public License for more details.                         */
/*                                                                                   */
/*      You should have received a copy of the GNU General Public License            */
/*      along with Juggluco. If not, see <https://www.gnu.org/licenses/>.            */
/*                                                                                   */
/*      Fri Jan 27 12:35:35 CET 2023                                                 */


#include "logs.hpp"
#include "fromjava.h"
#include "settings/settings.hpp"

#ifndef NOLOG 
#include <signal.h>
extern bool dolog;
#endif

#include "destruct.hpp"
#ifndef NOLOG
extern "C" const char *gettracelogpath();
extern "C" void tracelogemptied();
#endif
#include <dirent.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
extern pathconcat logbasedir;

 #include <dlfcn.h>

/*
static void *getdlsym(void * handle, const char *symbol) {
       void *res=dlsym( handle, symbol);
       if(!res) {
         LOGGER("dlsym(handle,%s)==null\n",symbol);
         }
       return res;
       }
    
static pid_t  realfork(void) {
	typedef pid_t (*fork_t)(void);
  	static fork_t fo=(fork_t)getdlsym(RTLD_NEXT,"fork");
	pid_t pid=fo();
	LOGGER("fork()=%d\n",pid);
	return pid;
	} */

#ifndef NOLOG 
template<typename T,typename  ... Ts>
pid_t  process(T com,Ts... args)  {
        if(pid_t childpid=fork())
            return childpid;
        execl(com,com,args...,NULL);
        lerror(com);
        exit(4);
        return 0;
        }

pid_t logcatpid=0;
#include <sys/wait.h>
static void  sig_chld(int signo) {
    LOGAR("sig_child");
    pid_t pid;
    int stat;
    while((pid=sys_wait4(-1, &stat, WNOHANG,nullptr)) > 0)
            LOGGER("child %d terminated\n", pid);
    return;
  }
void killlogcat() {
    LOGGER("kill %d\n",logcatpid);
     if(logcatpid) {
        signal(SIGCHLD, sig_chld);
        kill(logcatpid,SIGTERM);
        logcatpid=0;
        }
     }

static destruct _([]{killlogcat();});

/*	logcat used to be piped into logcat.txt with a plain shell append: no
	size limit, the whole ring buffer re-dumped on every start, and the
	child outliving an app process that gets killed without running the
	static destructor. Several of those writers appending forever is what
	fills up the device. Let logcat do its own bounded rotation instead,
	and only tail new lines.					*/
#ifndef LOGCATROTATEKB
#define LOGCATROTATEKB 2048
#endif
#ifndef LOGCATROTATEFILES
#define LOGCATROTATEFILES 3
#endif
constexpr const long maxlogcatbytes=(long)LOGCATROTATEKB*1024L*(LOGCATROTATEFILES+1);

/*	rotated generations are logcat.txt.1 ... logcat.txt.<LOGCATROTATEFILES>	*/
static void logcatrotation(char *buf,int max,const char *base,int nr) {
    snprintf(buf,max,"%s.%d",base,nr);
    }

static long logcatbytes(const char *base) {
    long total=0;
    struct stat st;
    if(stat(base,&st)==0)
        total=st.st_size;
    constexpr const int maxpath=PATH_MAX;
    char rotated[maxpath];
    for(int i=1;i<=LOGCATROTATEFILES;i++) {
        logcatrotation(rotated,maxpath,base,i);
        if(stat(rotated,&st)==0)
            total+=st.st_size;
        }
    return total;
    }

static void removelogcatrotations(const char *base) {
    constexpr const int maxpath=PATH_MAX;
    char rotated[maxpath];
    for(int i=1;i<=LOGCATROTATEFILES;i++) {
        logcatrotation(rotated,maxpath,base,i);
        unlink(rotated);
        }
    }

/*	An app process killed by the system never runs killlogcat(), so a
	previous logcat can still be appending to the same file. Only our own
	uid is visible in /proc, so anything left running logcat is ours.  */
static void killstalelogcat() {
    DIR *dir=opendir("/proc");
    if(!dir) {
        lerror("opendir(/proc)");
        return;
        }
    destruct _dirdes([dir]{closedir(dir);});
    const pid_t self=getpid();
    const uid_t us=getuid();
    while(const struct dirent *ent=readdir(dir)) {
        const char *name=ent->d_name;
        if(name[0]<'0'||name[0]>'9')
            continue;
        const pid_t pid=atoi(name);
        if(pid<=0||pid==self)
            continue;
        constexpr const int maxpath=64;
        char procpath[maxpath];
        snprintf(procpath,maxpath,"/proc/%d",pid);
        struct stat st;
        if(stat(procpath,&st)!=0||st.st_uid!=us)
            continue;
        snprintf(procpath,maxpath,"/proc/%d/cmdline",pid);
        const int handle=open(procpath,O_RDONLY);
        if(handle<0)
            continue;
        constexpr const int maxcmd=512;
        char cmdline[maxcmd];
        const ssize_t len=read(handle,cmdline,maxcmd-1);
        close(handle);
        if(len<=0)
            continue;
        cmdline[len]='\0';
        for(ssize_t i=0;i<len-1;i++) {	/*arguments are NUL separated*/
            if(cmdline[i]=='\0')
                cmdline[i]=' ';
            }
        if(strstr(cmdline,"logcat")) {
            LOGGER("killing stale logcat %d: %s\n",pid,cmdline);
            kill(pid,SIGKILL);
            }
        }
    }

void startlogcat() {
    LOGGER("startlogcat %p",logbasedir.data());
    if(logbasedir.data()) {
        pathconcat logcatfile(logbasedir,"logcat.txt");
        const char *base=logcatfile.data();
        killstalelogcat();
        if(logcatbytes(base)>maxlogcatbytes) {	/*Grown without a cap before*/
            truncate(base,0);
            removelogcatrotations(base);
            }
        constexpr const int maxnum=16;
        char kbytes[maxnum],generations[maxnum];
        snprintf(kbytes,maxnum,"%d",LOGCATROTATEKB);
        snprintf(generations,maxnum,"%d",LOGCATROTATEFILES);
        logcatpid=process("/system/bin/logcat","-T","1","-f",base,"-r",kbytes,"-n",generations);
        LOGGER("logcatpid=%d\n",logcatpid);
        }
    }

#include <sys/sendfile.h>


decltype(std::declval<struct stat>().st_size) filesize(int handle) {
    if(handle==-1) {
        LOGAR("getlogfile()=-1");
        return -1;
    }
    struct stat st;
    if(int res=fstat(handle,&st)) {
        flerror("fstat(%d)=%d",handle,res);
        return -1;
    }
    return st.st_size;
}
extern int getlogfile();
auto logfilesize() {
    return filesize(getlogfile());
    }

extern          pathconcat logfile;


static bool copyfile(const char *infile,int out) {
#ifndef NOLOG 
    destruct   _([out]{ close(out);});
    int in=open(infile, O_RDONLY);
    if(in==-1) {
        LOGGER("handle %s = 0\n",infile);
        return false;
        }
    destruct   _2([in]{ close(in);});
    auto len=filesize(in);
    if(len<=0) {
        LOGGER("size %s=%d\n",infile,len);
        return false;
        }
   auto outlen= sendfile(out, in, nullptr,len);
    bool suc=outlen==len;
   if(!suc)  {
       flerror("sendfile(%d,%d (%s) ,null,%ld)=%zd",out,in,infile,len,outlen);
       }
    return suc; 
#else
    return false;
#endif
    }
#endif
extern "C" JNIEXPORT void JNICALL fromjava(log)(JNIEnv *env, jclass thiz,jstring jmess) {
#ifndef NOLOG 
    if(dolog) {
        const jint len = env->GetStringUTFLength( jmess);
        char mess[len+1];
        jint jlen = env->GetStringLength( jmess);
        env->GetStringUTFRegion(jmess, 0,jlen, mess);
        mess[len]='\0';
        LOGGERNO(mess,len,false);
        }
#endif
    }


extern "C"  JNIEXPORT jboolean JNICALL fromjava(saveLog)(JNIEnv *envin, jclass thiz,jint out) {
#ifndef NOLOG 
    return copyfile(logfile.data(), out);
#else
    return false;
#endif
    }
extern "C"  JNIEXPORT jboolean JNICALL fromjava(saveLogcat)(JNIEnv *envin, jclass thiz,jint out) {
#ifndef NOLOG 
    pathconcat logfile(logbasedir,"logcat.txt");
    return copyfile(logfile.data(), out);
#else
    return false;
#endif
    }

extern "C"  JNIEXPORT void JNICALL fromjava(zeroLogcat)(JNIEnv *env, jclass thiz) {
#ifndef NOLOG 
     pathconcat logfile(logbasedir,"logcat.txt");
     truncate(logfile.data(),0);
     removelogcatrotations(logfile.data());
#endif
     }
extern "C"  JNIEXPORT jlong JNICALL fromjava(getLogcatfilesize)(JNIEnv *env, jclass thiz) {
#ifndef NOLOG
    pathconcat logcatfile(logbasedir,"logcat.txt");
    if(!logcatfile.data())
        return -1L;
    return logcatbytes(logcatfile.data());
#else
    return -1L;
#endif
    }
extern "C"  JNIEXPORT void JNICALL fromjava(dologcat)(JNIEnv *envin, jclass thiz,jboolean val) {
#ifndef NOLOG 
        if(val!=settings->data()->logcat) {
              settings->data()->logcat=val;
              killlogcat();
              if(val)
                  startlogcat();
               }
#endif
    }
extern "C"  JNIEXPORT jboolean JNICALL fromjava(islogcat)(JNIEnv *env, jclass thiz) {
#ifndef NOLOG
      return settings->data()->logcat;
#else
      return 0;
#endif
    }
    
extern "C"  JNIEXPORT void JNICALL fromjava(dolog)(JNIEnv *envin, jclass thiz,jboolean val) {
#ifndef NOLOG 
    if(settings) {
        settings->data()->nolog=!val;
        }
    dolog=val;
#endif
    }
extern "C"  JNIEXPORT jboolean JNICALL fromjava(islogging)(JNIEnv *env, jclass thiz) {
#ifndef NOLOG
    return dolog;
#else
    return 0;
#endif
    }

#include <sys/stat.h>

extern "C"  JNIEXPORT void JNICALL fromjava(zeroLog)(JNIEnv *env, jclass thiz) {
#ifndef NOLOG 
    if(const int handle=getlogfile();handle!=-1) 
        ftruncate(handle,0);
    if(const char *path=gettracelogpath()) {
        constexpr const int maxpath=PATH_MAX;
        char previous[maxpath];
        snprintf(previous,maxpath,"%s.1",path);
        unlink(previous);
        }
    tracelogemptied();
#endif
     }


extern "C"  JNIEXPORT jlong JNICALL fromjava(getLogfilesize)(JNIEnv *env, jclass thiz) {
#ifndef NOLOG
    auto total=logfilesize();
    if(total>0)  {
        if(const char *path=gettracelogpath()) {
            constexpr const int maxpath=PATH_MAX;
            char previous[maxpath];
            snprintf(previous,maxpath,"%s.1",path);
            struct stat st;
            if(stat(previous,&st)==0)
                total+=st.st_size;
            }
        }
    return total;
#else
    return -1L;
#endif
    }

/*
extern "C" JNIEXPORT void JNICALL fromjava(showbytes)(JNIEnv *env, jclass thiz, jstring jstr,jbyteArray jbytes) {
    if(jstr) {
        const char *str = env->GetStringUTFChars( jstr, NULL);
        if (str == nullptr) return ;
        destruct   dest([jstr,str,env]() {env->ReleaseStringUTFChars(jstr, str);});
        if(jbytes) {
            const jsize lens=env->GetArrayLength(jbytes);
            uint8_t bytes[lens];
            env->GetByteArrayRegion(jbytes, 0, lens, (jbyte *)bytes);
            hexstr hex(bytes,lens);
            LOGGER("%s: %s\n",str,hex.str());
            }
        else
            LOGGER("%s zero bytes array\n",str);
        }
    } */


extern "C" JNIEXPORT void JNICALL fromjava(showbytes)(JNIEnv *env, jclass thiz,jstring jmess,jbyteArray jar) {
#ifndef NOLOG
    if(dolog) {
        const jint len = env->GetStringUTFLength(jmess);
        

        constexpr const char nullstr[]=" null";
        constexpr const int nulllen=sizeof(nullstr)-1;
            const jsize vallen=jar?env->GetArrayLength(jar):0;

        constexpr const int startlen=0;
        const int totlen=startlen+len+(jar?(vallen*3):nulllen)+1;
        char mess[totlen];
        const jint jlen = env->GetStringLength(jmess);
        env->GetStringUTFRegion(jmess, 0,jlen, mess+startlen);
        int pos=startlen+len;
        if(jar) {
            if(vallen) {
                uint8_t value[vallen];
                env->GetByteArrayRegion(jar, 0, vallen,(jbyte *) value);
                for(int i=0;i<vallen;i++) {
                    pos+=sprintf(mess+pos," %02X",value[i]);
                    }
                }
            }
        else {
            memcpy(mess+pos,nullstr,nulllen);
            pos+=nulllen;
            }
        LOGGERN(mess,pos);
        }
#endif
        }


