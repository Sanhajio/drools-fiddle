package com.rest;

import org.drools.compiler.compiler.DrlParser;
import org.drools.compiler.compiler.DroolsError;
import org.drools.compiler.compiler.DroolsParserException;
import org.drools.compiler.lang.descr.AbstractClassTypeDeclarationDescr;
import org.drools.compiler.lang.descr.PackageDescr;
import org.drools.verifier.Verifier;
import org.drools.verifier.VerifierError;
import org.drools.verifier.builder.VerifierBuilder;
import org.drools.verifier.builder.VerifierBuilderFactory;
import org.drools.verifier.data.VerifierReport;
import org.drools.verifier.report.components.Severity;
import org.drools.verifier.report.components.VerifierMessageBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.type.FactField;
import org.kie.api.definition.type.FactType;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Path("/message")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageRestService {

  @GET
  public Message printMessage() {
    Message aMessage = new Message();
    aMessage.setId(1);
    aMessage.setData("Hello World!");
    return aMessage;
  }

  @POST
  @Path("/droolsverifier")
  public Message postDroolsVerifier(Message iMessage) {
    System.out.println("Init validation drl: DroolsVerifier");
    StringBuilder aLog = new StringBuilder();

    VerifierBuilder vBuilder = VerifierBuilderFactory.newVerifierBuilder();

    Verifier verifier = vBuilder.newVerifier();

    verifier.addResourcesToVerify( ResourceFactory.newByteArrayResource(iMessage.getData().getBytes()), ResourceType.DRL );

    if (verifier.hasErrors()) {
      for (VerifierError error : verifier.getErrors()) {
        System.out.println(error.getMessage());
        aLog.append(error.getMessage() + "\n");
      }
    } else {

      verifier.fireAnalysis();

      VerifierReport result = verifier.getResult();
      for (VerifierMessageBase base : result.getBySeverity(Severity.WARNING)) {
        System.out.println(base);
        aLog.append(base + "\n");
      }
    }
    iMessage.setLog(aLog.toString());
    System.out.println(iMessage);

    return iMessage;
  }

  @POST
  @Path("/drlParser")
  public Message postDrlParser(Message iMessage) throws DroolsParserException {
    System.out.println("Init validation drl: DrlParser");
    StringBuilder aLog = new StringBuilder();

    DrlParser parser = new DrlParser();
    PackageDescr descr = parser.parse(true, iMessage.getData());
    System.out.println(parser.getErrors());

    if (parser.hasErrors()) {
      for (DroolsError error : parser.getErrors()) {
        System.out.println("ERROR:\n" + error.getMessage());
        aLog.append(error.getMessage() + "\n");
      }
    } else {
      System.out.println("List facts");
      List<Fact> facts = new ArrayList<Fact>();
      for (AbstractClassTypeDeclarationDescr declare : descr.getClassAndEnumDeclarationDescrs()) {
        Fact aFact = new Fact();
        aFact.setId(declare.getLine());
        List<Attribute> attributes = new ArrayList<Attribute>();
        aFact.setName(declare.getTypeName());
        for (String field : declare.getFields().keySet()) {
          Attribute attr = new Attribute();
          attr.setId(declare.getFields().get(field).getLine());
          attr.setName(field);
          attr.setType(declare.getFields().get(field).getPattern().getObjectType());
          attributes.add(attr);
        }
        aFact.setAttributes(attributes);
        facts.add(aFact);
      }
      List<Package> packs = new ArrayList<Package>();
      Package pack = new Package();
      pack.setName("default");
      pack.setFacts(facts);
      packs.add(pack);
      iMessage.setPackages(packs);
      aLog.append(descr.getName());
    }
    iMessage.setLog(aLog.toString());
    System.out.println(iMessage);

    return iMessage;
  }

  @POST
  @Path("/drlCompile")
  public Message postDrlCompile(Message iMessage) throws DroolsParserException {
    System.out.println("Init validation drl: DrlParser");
    StringBuilder aLog = new StringBuilder();

    KieServices ks = KieServices.Factory.get();
    KieRepository kr = ks.getRepository();
    KieFileSystem kfs = ks.newKieFileSystem();

    kfs.write("src/main/resources/temp.drl", iMessage.getData());

    KieBuilder kb = ks.newKieBuilder(kfs);
    KieContainer kContainer;
    kb.buildAll(); // kieModule is automatically deployed to KieRepository if successfully built.
    if (kb.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
      for (org.kie.api.builder.Message info : kb.getResults().getMessages()) {
        System.out.println(info.toString());
        aLog.append(info.toString() + "\n");
      }
    } else {
      kContainer = ks.newKieContainer(kr.getDefaultReleaseId());
      aLog.append(kContainer.getClassLoader());

      // packages parsing
      List<Package> packs = new ArrayList<Package>();
      for (KiePackage pack : kContainer.getKieBase().getKiePackages()) {
        Package aPack = new Package();
        aPack.setName(pack.getName());
        List<Fact> facts = new ArrayList<Fact>();
        for (FactType declare : pack.getFactTypes()) {
          Fact aFact = new Fact();
          List<Attribute> attributes = new ArrayList<Attribute>();
          aFact.setName(declare.getName());
          for (FactField field : declare.getFields()) {
            Attribute attr = new Attribute();
            attr.setId(field.getIndex());
            attr.setName(field.getName());
            attr.setType(field.getType().getCanonicalName());
            attributes.add(attr);
          }
          aFact.setAttributes(attributes);
          facts.add(aFact);
        }
        aPack.setFacts(facts);
        packs.add(aPack);
      }
      iMessage.setPackages(packs);
    }

    iMessage.setLog(aLog.toString());
    System.out.println(iMessage);

    return iMessage;
  }

}