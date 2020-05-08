package org.openpaas.ieda.deploy.web.config.systemRelease.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.openpaas.ieda.common.api.LocalDirectoryConfiguration;
import org.openpaas.ieda.common.exception.CommonException;
import org.openpaas.ieda.common.service.CommonUtils;
import org.openpaas.ieda.deploy.web.common.service.CommonDeployService;
import org.openpaas.ieda.deploy.web.common.service.CommonDeployUtils;
import org.openpaas.ieda.deploy.web.config.systemRelease.dao.ReleaseManagementDAO;
import org.openpaas.ieda.deploy.web.config.systemRelease.dao.ReleaseManagementVO;
import org.openpaas.ieda.deploy.web.config.systemRelease.dto.ReleaseManagementDTO;
import org.openpaas.ieda.deploy.web.management.code.dao.CommonCodeDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReleaseManagementService {
    
    @Autowired private ReleaseManagementDAO dao;
    @Autowired private CommonCodeDAO commonCodeDao;
    @Autowired private CommonDeployService commonService;
    @Autowired private MessageSource message;
    
    final private static String CLOUDFOUNDRYURL = "https://bosh.io/d/github.com/cloudfoundry/";
    final private static String CLOUDFOUNDRYINCUBATORURL = "https://bosh.io/d/github.com/cloudfoundry-incubator/";
    final private static String CPPFORLIFEURL = "https://bosh.io/d/github.com/cppforlife/";
    final private static String SEPARATOR = System.getProperty("file.separator");
    final private static String TMPDIRECTORY = LocalDirectoryConfiguration.getTmpDir();
    final private static String RELEASEDIRECTORY = LocalDirectoryConfiguration.getReleaseDir();
    final private static String LOCK_DIR=LocalDirectoryConfiguration.getLockDir();
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 시스템 릴리즈 정보 목록 조회
     * @title : getSystemReleaseList
     * @return : List<ReleaseManagementVO>
    ***************************************************/
    public List<ReleaseManagementVO> getSystemReleaseList(){
        //listed release search in the database
        List<ReleaseManagementVO> releaseList = dao.selectSystemReleaseList();
        if(!releaseList.isEmpty()){
            for( ReleaseManagementVO release : releaseList ){
                if( release.getDownloadStatus() != null ){
                    if( release.getDownloadStatus().toUpperCase().equalsIgnoreCase("DOWNLOADED") ){
                        File releaseFile = new File( RELEASEDIRECTORY +SEPARATOR + release.getReleaseFileName());
                        if(!releaseFile.exists()){
                            ReleaseManagementDTO.Delete dto = new ReleaseManagementDTO.Delete();
                            dto.setId( String.valueOf(release.getId()) );
                            dto.setReleaseFileName(release.getReleaseFileName());
                            dao.deleteSystemRelase(dto);
                        }
                    }
                }
            }
        }
        return dao.selectSystemReleaseList();
    }
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 로컬에 저장된 릴리즈 목록 조회 
     * @title : getLocalReleaseList
     * @return : List<String>
    ***************************************************/
    public List<String> getLocalReleaseFileList() {
        //1.파일객체 생성
        File dir = new File(RELEASEDIRECTORY);
        //2.폴더가 가진 파일객체를 리스트로 받는다.
        File[] localFiles = dir.listFiles();
        List<String> localReleases = new ArrayList<>();
        if( localFiles != null && localFiles.length > 0){
            for (File file : localFiles) {
                localReleases.add(file.getName());
            }
        }
        return localReleases;
    }
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 공통 릴리즈 유형 콤보 조회 
     * @title : getSystemReleaseTypeList
     * @return : List<String>
    ***************************************************/
    public List<String> getSystemReleaseTypeList(){
        return commonCodeDao.selectReleaseTypeList("RELEASE_TYPE");
    }
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 공통 릴리즈 콤보
     * @title : getLocalReleaseList
     * @return : List<String>
    ***************************************************/
    public List<String> getLocalReleaseList(String type, String iaas){
        return dao.selectLocalReleaseList(type, iaas);
    }
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 로컬 파일 업로드에 대한 시스템 릴리즈 정보 저장
     * @title : registSystemReleaseUploadInfo
     * @return : ReleaseManagementVO
    ***************************************************/
    public ReleaseManagementVO saveSystemReleaseFileUploadInfo(ReleaseManagementDTO.Regist dto, Principal principal){
        String fileName = dto.getReleaseFileName();
        String status = "";
        Boolean.valueOf("String");
        ReleaseManagementVO vo = null;
        if( fileName.indexOf(".tgz") < 0 && fileName.indexOf(".zip") < 0 ){
            status = "error";
            CommonDeployUtils.deleteFile(LOCK_DIR, fileName.split(".tgz")[0]+"-download.lock");
            throw new CommonException(message.getMessage("common.badRequest.exception.code", null, Locale.KOREA),
                    message.getMessage("common.extension.badRequest.message", null, Locale.KOREA), HttpStatus.BAD_REQUEST);
        }
        if(StringUtils.isEmpty(dto.getReleaseFileName()) || Long.parseLong(dto.getReleaseSize()) < 1 ){
            status = "error";
            CommonDeployUtils.deleteFile(LOCK_DIR, fileName.split(".tgz")[0]+"-download.lock");
            throw new CommonException(message.getMessage("common.badRequest.exception.code", null, Locale.KOREA),
                    message.getMessage("common.badRequest.message", null, Locale.KOREA), HttpStatus.BAD_REQUEST);
        }
        //release path
        File releseFile = new File(RELEASEDIRECTORY + SEPARATOR + dto.getReleaseFileName());
        
        //릴리즈 파일이 존재하고 덮어쓰기 체크가 안되어 있을 경우
        if(releseFile.exists() && "false".equalsIgnoreCase(dto.getOverlayCheck())) {
            status = "conflict";
            CommonDeployUtils.deleteFile(LOCK_DIR, fileName.split(".tgz")[0]+"-download.lock");
            throw new CommonException(message.getMessage("common.conflict.exception.code", null, Locale.KOREA),
                    message.getMessage("common.conflict.file.message", null, Locale.KOREA), HttpStatus.CONFLICT);
        }else{
            status = "done";
            // 릴리즈 사이즈 format
            long releaseSize = Long.parseLong(dto.getReleaseSize());
            dto.setReleaseSize(CommonUtils.formatSizeUnit(releaseSize));
            dto.setDownloadStatus("DOWNLOADING");
            vo = saveSystemReleaseInfo(dto, principal);
        }
        return vo;
    }
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : Exception 발생 시 lock 파일 삭제 
     * @title : deleteLockFile
     * @return : Boolean
    ***************************************************/
    public Boolean deleteLockFile(String status,String  fileName){
        Boolean flag = false;
        int index;
        String lockFileName= "";
        if( !status.equalsIgnoreCase("done")){
            //lock file delete
            if( !StringUtils.isEmpty(fileName) ){
                try{
                     index = fileName.lastIndexOf(".");
                     lockFileName = fileName.substring(0, index);
                }catch (StringIndexOutOfBoundsException e) {
                    throw new CommonException(message.getMessage("common.badRequest.exception.code", null, Locale.KOREA),
                            message.getMessage("common.badRequest.message", null, Locale.KOREA), HttpStatus.BAD_REQUEST);
                }
                CommonDeployUtils.deleteFile(LOCK_DIR, lockFileName+"-download.lock");
            }
        }
        return flag;
    }

    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : URL을 통한 릴리즈 다운로드 정보 저장
     * @title : registSystemReleaseDownloadInfo
     * @return : ReleaseManagementVO
    ***************************************************/
    public ReleaseManagementVO checkSystemReleaseDownloadedFileInfoByWget(ReleaseManagementDTO.Regist dto, Principal principal){
        String status = "";
        ReleaseManagementVO vo = null;
        
        if(dto.getReleaseFileName() == null || dto.getReleaseFileName().isEmpty()){
            status ="notfound";
            deleteLockFile(status, dto.getReleaseFileName());
            throw new CommonException(message.getMessage("common.badRequest.exception.code", null, Locale.KOREA),
                    message.getMessage("common.badRequest.message", null, Locale.KOREA), HttpStatus.BAD_REQUEST);
        }
        //create lock file 
        int index = dto.getReleaseFileName().lastIndexOf(".");
        String lockFile = dto.getReleaseFileName().substring(0, index);
        Boolean checkLock = commonService.lockFileSet(lockFile +"-download");
        if( !checkLock ){
            throw new CommonException(message.getMessage("common.conflict.exception.code", null, Locale.KOREA), 
                    message.getMessage("common.lock.conflict.message", null, Locale.KOREA), HttpStatus.CONFLICT);
        }
        
        //release path
        File releseFile = new File(RELEASEDIRECTORY + SEPARATOR + dto.getReleaseFileName());
        
        //릴리즈 파일이 존재하고 덮어쓰기 체크가 안되어 있을 경우
        if(releseFile.exists() && "false".equalsIgnoreCase(dto.getOverlayCheck())) {
            status ="error";
            deleteLockFile(status, dto.getReleaseFileName()); //lock 파일 삭제
            throw new CommonException(message.getMessage("common.conflict.exception.code", null, Locale.KOREA),
                    message.getMessage("common.conflict.file.message", null, Locale.KOREA), HttpStatus.CONFLICT);
        }else{
            // 릴리즈 사이즈 format
            status ="done";
            dto.setReleaseSize(CommonUtils.formatSizeUnit(Long.parseLong(dto.getReleaseSize())));
            dto.setReleaseFileName(dto.getReleaseFileName());
            dto.setDownloadStatus("DOWNLOADING");
            vo = saveSystemReleaseInfo(dto, principal);
        }
        return vo;
    }
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 릴리즈 다운로드 링크 설정 후 Wget 메소드 호출
     * @title : saveSystemReleaseUrlInfo
     * @return : ReleaseManagementVO
    ***************************************************/
    public ReleaseManagementVO saveSystemReleaseUrlInfo(ReleaseManagementDTO.Regist dto, Principal principal) {
        if(StringUtils.isEmpty(dto.getReleasePathUrl()) && StringUtils.isEmpty(dto.getReleasePathVersion())){
            deleteLockFile("error", dto.getReleaseFileName());
            throw new CommonException(message.getMessage("common.badRequest.exception.code", null, Locale.KOREA),
                    message.getMessage("common.badRequest.message", null, Locale.KOREA), HttpStatus.BAD_REQUEST);
        }
        setReleaseDownloadLink(dto);
        return getSystemReleaseInfoByWget(dto, principal);
    }
    
    /***************************************************
    * @project : Paas 플랫폼 설치 자동화
    * @description : 릴리즈 다운로드 링크 정보 설정
    * @title : setReleaseDownloadLink
    * @return : void
    ***************************************************/
    public void setReleaseDownloadLink(ReleaseManagementDTO.Regist dto){
        String releaseDownloadLink = "";
        if(dto.getFileType().equalsIgnoreCase("URL")){
            releaseDownloadLink = dto.getReleasePathUrl();
        }else{
            releaseDownloadLink = setDownloadBaseURLByReleaseVersion(dto);
        }
        dto.setDownloadLink(releaseDownloadLink);
    }
    
    /***************************************************
    * @project : Paas 플랫폼 설치 자동화
    * @description : wget을 통해 릴리즈 파일 이름, 사이즈 정보 추출
    * @title : getSystemReleaseInfoByWget
    * @return : ReleaseManagementVO
    ***************************************************/
    public ReleaseManagementVO getSystemReleaseInfoByWget(ReleaseManagementDTO.Regist dto, Principal principal){
        if( StringUtils.isEmpty(dto.getDownloadLink()) ) {
            throw new CommonException(message.getMessage("common.badRequest.exception.code", null, Locale.KOREA),
                    message.getMessage("common.badRequest.message", null, Locale.KOREA), HttpStatus.BAD_REQUEST);
        }
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        String[] search = null;
        String info = null;
        Process process = null;
        StringBuffer accumulatedBuffer = new StringBuffer("");
        boolean flag = false;
        try{
            //wget 실행
            ProcessBuilder builder = new ProcessBuilder("wget", "--spider", "-d", "-P", TMPDIRECTORY, "--content-disposition", dto.getDownloadLink());
            builder.redirectErrorStream(true);
            process = builder.start();
            inputStream = process.getInputStream();
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            while ((info = bufferedReader.readLine()) != null){ 
                accumulatedBuffer.append(info).append("\n");
                if(info.contains("Content-Disposition:") && !flag){
                    search = info.split("filename=");
                    dto.setReleaseFileName(search[search.length-1]);
                    flag = true;
                }
                if(info.contains("Content-Length:")){
                    search = info.split(" ");
                    dto.setReleaseSize(search[search.length-1]);
                }
            }
            String accumulatedLog = accumulatedBuffer.toString();
            if(accumulatedLog.toUpperCase().contains("INTERNAL SERVER ERROR")){
                throw new CommonException(message.getMessage("common.badRequest.exception.code", null, Locale.KOREA),
                        message.getMessage("common.badRequest.message", null, Locale.KOREA), HttpStatus.BAD_REQUEST);
            }
        }catch(IOException e){
            throw new CommonException(message.getMessage("common.internalServerError.exception.code", null, Locale.KOREA),
                    message.getMessage("common.internalServerError.message", null, Locale.KOREA), HttpStatus.INTERNAL_SERVER_ERROR);
        }finally {
            if(bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    throw new CommonException(message.getMessage("common.internalServerError.exception.code", null, Locale.KOREA),
                            message.getMessage("common.internalServerError.message", null, Locale.KOREA), HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return checkSystemReleaseDownloadedFileInfoByWget(dto, principal);
    }
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 릴리즈 버전별 다운로드 url 생성
     * @title : setDownloadBaseURLByReleaseVersion
     * @return : String
    ***************************************************/
    public String setDownloadBaseURLByReleaseVersion(ReleaseManagementDTO.Regist dto) {
        String boshCpiUrl = "";
        if(dto.getReleaseType().toLowerCase().equalsIgnoreCase("bosh_cpi")){
            boshCpiUrl = "bosh-"+dto.getIaasType().toLowerCase()+"-cpi-release?v=";
            if(!dto.getIaasType().toLowerCase().equals("warden")) {
                //dto.setDownloadLink(CLOUDFOUNDRYINCUBATORURL + boshCpiUrl + dto.getReleasePathVersion());
                dto.setDownloadLink(CLOUDFOUNDRYURL + boshCpiUrl + dto.getReleasePathVersion());
            }else{
                dto.setDownloadLink(CPPFORLIFEURL + boshCpiUrl + dto.getReleasePathVersion());
            }
        } else if(dto.getReleaseType().equalsIgnoreCase("etcd")){
            dto.setDownloadLink(CLOUDFOUNDRYINCUBATORURL + dto.getReleaseType().toLowerCase() + "-release?v=" + dto.getReleasePathVersion());
        } else if(dto.getReleaseType().equalsIgnoreCase("bosh")){
            dto.setDownloadLink(CLOUDFOUNDRYURL + dto.getReleaseType().toLowerCase() + "?v=" + dto.getReleasePathVersion());
        } else if( dto.getReleaseType().equalsIgnoreCase("os-conf")){
            dto.setDownloadLink(CLOUDFOUNDRYURL + "os-conf-release?v=" + dto.getReleasePathVersion());
        }else if( dto.getReleaseType().equalsIgnoreCase("bpm") ){
            dto.setDownloadLink(CLOUDFOUNDRYINCUBATORURL + dto.getReleaseType().toLowerCase() + "-release?v=" + dto.getReleasePathVersion());
        }else if( dto.getReleaseType().equalsIgnoreCase("bpm") ){
       	 dto.setDownloadLink(CLOUDFOUNDRYINCUBATORURL + "bpm-release?v=" + dto.getReleasePathVersion());
        }else if( dto.getReleaseType().equalsIgnoreCase("garaden-runc") ){
            dto.setDownloadLink(CLOUDFOUNDRYURL + dto.getReleaseType().toLowerCase()+"-release?v=" + dto.getReleasePathVersion());
        }else if( dto.getReleaseType().equalsIgnoreCase("bosh-virtualbox-cpi") ){
            dto.setDownloadLink(CPPFORLIFEURL + dto.getReleaseType().toLowerCase()+ "-release?v=" + dto.getReleasePathVersion());
        }else{
            dto.setDownloadLink(CLOUDFOUNDRYURL + dto.getReleaseType().toLowerCase()+"-release?v=" + dto.getReleasePathVersion());
        }
        return dto.getDownloadLink();
    }

    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 릴리즈 업로드 공통 정보 저장
     * @title : saveSystemReleaseInfo
     * @return : ReleaseManagementVO
    ***************************************************/
    public ReleaseManagementVO saveSystemReleaseInfo(ReleaseManagementDTO.Regist dto, Principal principal){
        ReleaseManagementVO vo = null;
        ReleaseManagementVO fileCheckResult = null;
        try{
            fileCheckResult = dao.selectSystemRelease(dto.getReleaseFileName());
            if( fileCheckResult == null ){
                dto.setCreateUserId(principal.getName());
                dto.setUpdateUserId(principal.getName());
                dao.insertSystemRelease(dto);
            } else{
                dto.setUpdateUserId(principal.getName());
                dto.setId(fileCheckResult.getId());
                dao.updateSystemRelease(dto);
            }
            vo = dao.selectSystemReleaseById(dto.getId());
            
        } catch(RuntimeException e){
            throw new CommonException(message.getMessage("common.internalServerError.exception.code", null, Locale.KOREA),
                    message.getMessage("common.internalServerError.message", null, Locale.KOREA), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch(Exception e){
            throw new CommonException(message.getMessage("common.internalServerError.exception.code", null, Locale.KOREA),
                    message.getMessage("common.internalServerError.message", null, Locale.KOREA), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return vo;
    }
    
    
    /***************************************************
     * @project : Paas 플랫폼 설치 자동화
     * @description : 시스템 릴리즈 삭제
     * @title : deleteSystemRelease
     * @return : Boolean
    ***************************************************/
    public void deleteSystemRelease(ReleaseManagementDTO.Delete dto) {
        dao.deleteSystemRelase(dto);
        //delete lock file
        int index = dto.getReleaseFileName().indexOf(".tgz");
        String lockFileName = dto.getReleaseFileName().substring(0, index) + "-download.lock";
        CommonDeployUtils.deleteFile(LOCK_DIR, lockFileName);
        
        //delete release File
        File releaseFile = new File(RELEASEDIRECTORY + SEPARATOR + dto.getReleaseFileName());
        if(releaseFile.exists()){ 
            CommonDeployUtils.deleteFile(RELEASEDIRECTORY, dto.getReleaseFileName());
        }
    }
    
}
