p="app/src/main/java/com/jujuwan/player/MainActivity.kt"
t=open(p).read()
s=t.index("Surface(\n            modifier = Modifier.weight(1.2f)")
e=t.rindex("@Composable",0,t.index("fun BadgeChip("))
c3="""Surface(modifier=Modifier.weight(1.35f).fillMaxHeight(),shape=RoundedCornerShape(16.dp),color=CardDark,border=BorderStroke(2.dp,NeonPurple)){
Column(modifier=Modifier.padding(10.dp),verticalArrangement=Arrangement.SpaceBetween){
if(selectedStream!=null){val item=selectedStream!!;val ctx=LocalContext.current
val pv=if(mode=="LIVE")remember(item.streamUrl){ExoPlayer.Builder(ctx).build().apply{setMediaItem(MediaItem.fromUri(item.streamUrl));prepare();playWhenReady=true}}else null
DisposableEffect(item.streamUrl,mode){onDispose{pv?.release()}}
Column(modifier=Modifier.weight(1f)){
if(mode=="LIVE"&&pv!=null){
Surface(shape=RoundedCornerShape(8.dp),color=Color.Black,border=BorderStroke(1.dp,NeonPurpleGlow),modifier=Modifier.fillMaxWidth().height(105.dp).clickable{pv.release();onPlayFullScreen(item.name,item.streamUrl)}){AndroidView(factory={c->PlayerView(c).apply{player=pv;useController=false}},update={it.player=pv},modifier=Modifier.fillMaxSize())}
Spacer(Modifier.height(4.dp));Text(item.name,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1);Spacer(Modifier.height(4.dp))
LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){items(epgList){l->Text(l,color=Color(0xFF38BDF8),fontSize=10.sp,maxLines=1)}}
}else{Text(item.name,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){BadgeChip("4K / HD");BadgeChip("HEVC");BadgeChip("DTS / EAC3")};Spacer(Modifier.height(8.dp));Text(item.plot.ifBlank{"Flux prêt sur Juju Wan."},color=Color.LightGray,fontSize=11.sp)}
}
Button(onClick={pv?.release();onPlayFullScreen(item.name,item.streamUrl)},modifier=Modifier.fillMaxWidth().height(34.dp),contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=NeonPurple)){Icon(Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("Lancer en plein écran",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)}
}else{Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Sélectionnez une chaîne à gauche",color=Color.Gray,fontSize=12.sp)}}}}
"""
open(p,"w").write(t[:s]+c3+t[e:])
